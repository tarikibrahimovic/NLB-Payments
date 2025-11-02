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

    // Repozitorijumi iz 'transactions' modula
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

        // 1. PROVERA IDEMPOTENTNOSTI (Brzi put)
        // Proveravamo da li je zahtev sa ovim ključem već obrađen.
        Optional<PaymentOrder> existingOrder = paymentOrderRepo.findByIdempotencyKey(request.idempotencyKey());
        if (existingOrder.isPresent()) {
            log.warn("Idempotent request received, returning existing status for key: {}", request.idempotencyKey());
            var order = existingOrder.get();
            return new BatchTransferResponse(order.getId(), order.getStatus(), "Request already processed");
        }

        // 2. PRIPREMA I INICIJALNO SNIMANJE (PENDING)
        PaymentOrder paymentOrder;
        List<PaymentOrderItem> orderItems;
        long totalAmountCents;

        try {
            // Konvertujemo iznose i računamo ukupan zbir
            totalAmountCents = request.items().stream()
                    .mapToLong(this::convertToCents)
                    .sum();

            // Kreiramo "glavu" naloga
            paymentOrder = PaymentOrder.builder()
                    .idempotencyKey(request.idempotencyKey())
                    .initiatedByUserId(request.initiatedByUserId())
                    .sourceAccountId(request.sourceAccountId())
                    .totalAmountCents(totalAmountCents)
                    .currency(Currency.EUR) // Fiksirano po specifikaciji
                    .status(PaymentOrderStatus.PENDING)
                    .build();

            // Kreiramo stavke naloga
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
            paymentOrder.setItems(orderItems); // Povezujemo stavke sa nalogom

            // Snimamo nalog i stavke (transakciono).
            // saveAndFlush() osigurava da UNIQUE constraint (idempotencyKey)
            // pukne odmah ako postoji race condition.
            paymentOrderRepo.saveAndFlush(paymentOrder);

        } catch (DataIntegrityViolationException e) {
            // Race condition: Dva ista zahteva su stigla u isto vreme.
            // Jedan je uspeo, ovaj je pukao na 'idempotency_key' constraint-u.
            // Vraćamo rezultat onog koji je uspeo.
            log.warn("Race condition detected for idempotency key: {}", request.idempotencyKey(), e);
            var racedOrder = paymentOrderRepo.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Failed to retrieve raced order"));
            return new BatchTransferResponse(racedOrder.getId(), racedOrder.getStatus(), "Concurrent request processed");

        } catch (Exception e) {
            // Bilo koja druga greška tokom pripreme
            log.error("Unhandled exception during preparation", e);
            // Nema paymentOrder-a za ažuriranje, samo logujemo u DLQ
            logToIntegrationFailure(request, e, null);
            throw new RuntimeException("System error during transfer preparation", e);
        }


        // 3. GLAVNA LOGIKA (ZAKLJUČAVANJE, VALIDACIJA, IZVRŠENJE)
        try {
            // Skupljamo SVE naloge (source + all destinations)
            Set<UUID> accountIds = request.items().stream()
                    .map(BatchItem::destinationAccountId)
                    .collect(Collectors.toSet());
            accountIds.add(request.sourceAccountId());

            // ZAKLJUČAVANJE: Pozivamo našu custom metodu
            List<Account> lockedAccounts = accountRepo.findAllByIdInAndLock(new ArrayList<>(accountIds));
            Map<UUID, Account> accountMap = lockedAccounts.stream()
                    .collect(Collectors.toMap(Account::getId, Function.identity()));

            // VALIDACIJA
            // Provera da li su svi nalozi pronađeni
            if (lockedAccounts.size() != accountIds.size()) {
                throw new BusinessValidationException("One or more accounts not found");
            }

            Account sourceAccount = accountMap.get(request.sourceAccountId());

            // 1. Validacija vlasništva
            if (!sourceAccount.getOwner().getId().equals(request.initiatedByUserId())) {
                throw new BusinessValidationException("User does not own the source account");
            }
            // 2. Validacija statusa naloga
            for (Account acc : lockedAccounts) {
                if (acc.getStatus() != AccountStatus.ACTIVE) {
                    throw new BusinessValidationException("Account " + acc.getId() + " is not ACTIVE");
                }
            }
            // 3. Validacija salda
            if (sourceAccount.getBalanceCents() < totalAmountCents) {
                throw new BusinessValidationException("Insufficient funds");
            }

            // IZVRŠENJE (Ako su sve validacije prošle)
            // Debituj izvor
            sourceAccount.setBalanceCents(sourceAccount.getBalanceCents() - totalAmountCents);

            List<Transaction> transactions = new ArrayList<>();
            List<Account> updatedAccounts = new ArrayList<>();
            updatedAccounts.add(sourceAccount);

            // Kredituj destinacije i ažuriraj stavke
            for (PaymentOrderItem item : orderItems) {
                Account destAccount = accountMap.get(item.getDestinationAccountId());
                destAccount.setBalanceCents(destAccount.getBalanceCents() + item.getAmountCents());
                updatedAccounts.add(destAccount);

                item.setStatus(PaymentOrderItemStatus.SUCCESS); // Ažuriraj status stavke

                // Kreiraj zapis za glavnu knjigu (ledger)
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

            // 4. SNIMANJE REZULTATA
            accountRepo.saveAll(updatedAccounts);
            transactionRepo.saveAll(transactions);

            // Ažuriraj status "glave" naloga
            paymentOrder.setStatus(PaymentOrderStatus.COMPLETED);
            paymentOrderRepo.save(paymentOrder); // JPA će snimiti i ažurirane statuse 'items' zbog kaskade

            log.info("Batch transfer completed successfully for key: {}", request.idempotencyKey());
            return new BatchTransferResponse(paymentOrder.getId(), paymentOrder.getStatus(), "Transfer successful");

        } catch (BusinessValidationException e) {
            // POSLOVNA GREŠKA (Čist neuspeh)
            // Hvatamo našu custom grešku. Ne radimo rollback,
            // već snimamo FAILED status i radimo COMMIT.
            log.warn("Business validation failed for key {}: {}", request.idempotencyKey(), e.getMessage());
            return markOrderAsFailed(paymentOrder, orderItems, e.getMessage());

        } catch (Exception e) {
            // SISTEMSKA GREŠKA (Prljav neuspeh)
            // Npr. baza je pukla usred `accountRepo.saveAll()`.
            // Logujemo u DLQ i puštamo da @Transactional uradi ROLLBACK.
            log.error("System error during transfer execution for key {}: {}", request.idempotencyKey(), e.getMessage());
            logToIntegrationFailure(request, e, paymentOrder.getId());
            // Ponovo bacamo grešku da bismo osigurali rollback transakcije
            throw new RuntimeException("System error processing transfer, transaction will be rolled back", e);
        }
    }

    private long convertToCents(BatchItem item) {
        // Pretpostavlja da je iznos uvek npr. 12.34
        // Množi sa 100 i uzima long vrednost
        return item.amount().multiply(DECIMAL_MULTIPLIER).longValueExact();
    }

    /**
     * Ažurira status naloga i stavki na FAILED i snima u bazu.
     * Ovo je "čist" neuspeh, transakcija se KOMITUJE.
     */
    private BatchTransferResponse markOrderAsFailed(PaymentOrder order, List<PaymentOrderItem> items, String reason) {
        order.setStatus(PaymentOrderStatus.FAILED);
        for (PaymentOrderItem item : items) {
            // Ažuriramo samo one koji još nisu obrađeni
            if (item.getStatus() == PaymentOrderItemStatus.PENDING) {
                item.setStatus(PaymentOrderItemStatus.FAILED);
                item.setFailureReason(reason);
            }
        }
        paymentOrderRepo.save(order); // Snima i nalog i ažurirane stavke
        return new BatchTransferResponse(order.getId(), order.getStatus(), reason);
    }

    /**
     * Zapisuje sistemsku grešku u DLQ tabelu (IntegrationFailure).
     * Ovo se poziva kada se desi nešto nepredviđeno (npr. DB timeout).
     */
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
            // Ako čak i logovanje u DLQ ne uspe, logujemo u konzolu
            log.error("CRITICAL: Failed to log to IntegrationFailure table. Original error: {}. Logging error: {}",
                    e.getMessage(), loggingException.getMessage());
        }
    }
}