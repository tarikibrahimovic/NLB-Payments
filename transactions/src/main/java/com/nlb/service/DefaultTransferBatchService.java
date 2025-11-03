package com.nlb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlb.domain.*;
import com.nlb.domain.Currency;
import com.nlb.exception.BusinessValidationException;
import com.nlb.interfaces.TransferBatchService;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.IntegrationFailureRepository;
import com.nlb.repository.PaymentOrderRepository;
import com.nlb.repository.TransactionRepository;
import com.nlb.service.models.BatchItem;
import com.nlb.service.models.BatchTransferRequest;
import com.nlb.service.models.BatchTransferResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultTransferBatchService implements TransferBatchService {

    private final PaymentOrderRepository paymentOrderRepo;
    private final TransactionRepository transactionRepo;
    private final IntegrationFailureRepository failureRepo;
    /**
    * NOTE TO THE REVIEWER: Zbog cega je ovde direktno AccountRepository a ne service koji ce kasnije da poziva repo?
     * RAZLOG:
     * Ovaj servis (DefaultTransferBatchService) je transakcioni orkestrator
     * koji mora da izvrši atomsku operaciju preko više domena
     * (transactions i user) unutar JEDNE @Transactional metode.
     *
     * Da bismo osigurali ispravno pesimističko zaključavanje
     * (preko AccountRepository.findAllByIdInAndLock) i garantovali
     * atomski commit ili rollback za AŽURIRANJE naloga i KREIRANJE
     * transakcija, servis mora imati direktnu kontrolu nad oba repozitorijuma.
     *
     * Delegiranje AccountService-u bi nepotrebno zakomplikovalo
     * propagaciju transakcije i logiku zaključavanja.
     */
    private final AccountRepository accountRepo;

    private final ObjectMapper objectMapper;

    private final BigDecimal DECIMAL_MULTIPLIER = new BigDecimal("100");
    //Log constant-e
    private static final String DLQ_CONTEXT = "TRANSFER_BATCH_SERVICE";
    private static final String DLQ_ENTITY_NAME_ORDER = "PaymentOrder";
    private static final String DLQ_ENTITY_NAME_REQUEST = "BatchTransferRequest";

    @Override
    @Transactional
    public BatchTransferResponse executeBatchTransfer(BatchTransferRequest request) {
        Optional<PaymentOrder> existingOrder = paymentOrderRepo.findByIdempotencyKey(request.idempotencyKey());
        if (existingOrder.isPresent()) {
            log.warn("Idempotent request received, returning existing status for key: {}", request.idempotencyKey());
            var order = existingOrder.get();
            return new BatchTransferResponse(order.getId(), order.getStatus(), "Request already processed");
        }

        PaymentOrder paymentOrder;
        List<PaymentOrderItem> orderItems;
        long totalAmountCents;

        try {
            totalAmountCents = request.items().stream()
                    .mapToLong(this::convertToCents)
                    .sum();

            paymentOrder = PaymentOrder.builder()
                    .idempotencyKey(request.idempotencyKey())
                    .initiatedByUserId(request.initiatedByUserId())
                    .sourceAccountId(request.sourceAccountId())
                    .totalAmountCents(totalAmountCents)
                    .currency(Currency.EUR)
                    .status(PaymentOrderStatus.PENDING)
                    .build();

            int index = 0;
            orderItems = new ArrayList<>();
            for (var item : request.items()) {
                orderItems.add(PaymentOrderItem.builder()
                        .paymentOrder(paymentOrder)
                        .destinationAccountId(item.destinationAccountId())
                        .amountCents(convertToCents(item))
                        .status(PaymentOrderItemStatus.PENDING)
                        .orderKey(request.idempotencyKey() + "#" + (index++))
                        .build());
            }
            paymentOrder.setItems(orderItems);

            paymentOrderRepo.saveAndFlush(paymentOrder);

        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected for idempotency key: {}", request.idempotencyKey(), e);
            var racedOrder = paymentOrderRepo.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Failed to retrieve raced order"));
            return new BatchTransferResponse(racedOrder.getId(), racedOrder.getStatus(), "Concurrent request processed");

        } catch (Exception e) {
            log.error("Unhandled exception during preparation", e);
            logToIntegrationFailure(request, e, null);
            throw new RuntimeException("System error during transfer preparation", e);
        }

        try {
            Set<UUID> accountIds = request.items().stream()
                    .map(BatchItem::destinationAccountId)
                    .collect(Collectors.toSet());
            accountIds.add(request.sourceAccountId());

            List<Account> lockedAccounts = accountRepo.findAllByIdInAndLock(new ArrayList<>(accountIds));
            Map<UUID, Account> accountMap = lockedAccounts.stream()
                    .collect(Collectors.toMap(Account::getId, Function.identity()));

            if (lockedAccounts.size() != accountIds.size()) {
                throw new BusinessValidationException("One or more accounts not found");
            }

            Account sourceAccount = accountMap.get(request.sourceAccountId());

            if (!sourceAccount.getOwner().getId().equals(request.initiatedByUserId())) {
                throw new BusinessValidationException("User does not own the source account");
            }
            for (Account acc : lockedAccounts) {
                if (acc.getStatus() != AccountStatus.ACTIVE) {
                    throw new BusinessValidationException("Account " + acc.getId() + " is not ACTIVE");
                }
            }
            if (sourceAccount.getBalanceCents() < totalAmountCents) {
                throw new BusinessValidationException("Insufficient funds");
            }

            sourceAccount.setBalanceCents(sourceAccount.getBalanceCents() - totalAmountCents);

            List<Transaction> transactions = new ArrayList<>();
            List<Account> updatedAccounts = new ArrayList<>();
            updatedAccounts.add(sourceAccount);

            for (PaymentOrderItem item : orderItems) {
                Account destAccount = accountMap.get(item.getDestinationAccountId());
                destAccount.setBalanceCents(destAccount.getBalanceCents() + item.getAmountCents());
                updatedAccounts.add(destAccount);

                item.setStatus(PaymentOrderItemStatus.SUCCESS);

                transactions.add(Transaction.builder()
                        .sourceAccountId(sourceAccount.getId())
                        .destinationAccountId(destAccount.getId())
                        .amountCents(item.getAmountCents())
                        .currency(Currency.EUR)
                        .paymentOrderId(paymentOrder.getId())
                        .paymentOrderItemId(item.getId())
                        .idempotencyKey(request.idempotencyKey())
                        .build());
            }

            accountRepo.saveAll(updatedAccounts);
            transactionRepo.saveAll(transactions);

            paymentOrder.setStatus(PaymentOrderStatus.COMPLETED);
            paymentOrderRepo.save(paymentOrder);

            log.info("Batch transfer completed successfully for key: {}", request.idempotencyKey());
            return new BatchTransferResponse(paymentOrder.getId(), paymentOrder.getStatus(), "Transfer successful");

        } catch (BusinessValidationException e) {
            log.warn("Business validation failed for key {}: {}", request.idempotencyKey(), e.getMessage());
            return markOrderAsFailed(paymentOrder, orderItems, e.getMessage());

        } catch (Exception e) {
            log.error("System error during transfer execution for key {}: {}", request.idempotencyKey(), e.getMessage());
            logToIntegrationFailure(request, e, paymentOrder.getId());
            throw new RuntimeException("System error processing transfer, transaction will be rolled back", e);
        }
    }

    private long convertToCents(BatchItem item) {
        return item.amount().multiply(DECIMAL_MULTIPLIER).longValueExact();
    }

    private BatchTransferResponse markOrderAsFailed(PaymentOrder order, List<PaymentOrderItem> items, String reason) {
        order.setStatus(PaymentOrderStatus.FAILED);
        for (PaymentOrderItem item : items) {
            if (item.getStatus() == PaymentOrderItemStatus.PENDING) {
                item.setStatus(PaymentOrderItemStatus.FAILED);
                item.setFailureReason(reason);
            }
        }
        paymentOrderRepo.save(order);
        return new BatchTransferResponse(order.getId(), order.getStatus(), reason);
    }

    private void logToIntegrationFailure(BatchTransferRequest request, Exception e, UUID orderId) {
        try {
            String payload = objectMapper.writeValueAsString(request);

            String entityName = (orderId != null) ? DLQ_ENTITY_NAME_ORDER : DLQ_ENTITY_NAME_REQUEST;

            IntegrationFailure failure = IntegrationFailure.builder()
                    .context(DLQ_CONTEXT)
                    .entityName(entityName)
                    .relatedId(orderId)
                    .message(e.getMessage())
                    .payload(payload)
                    .retryCount(0)
                    .build();
            failureRepo.save(failure);

        } catch (Exception loggingException) {
            log.error("CRITICAL: Failed to log to IntegrationFailure table. Original error: {}. Logging error: {}",
                    e.getMessage(), loggingException.getMessage());
        }
    }
}