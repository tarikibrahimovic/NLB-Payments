package com.nlb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlb.domain.*;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.IntegrationFailureRepository;
import com.nlb.repository.PaymentOrderRepository;
import com.nlb.repository.TransactionRepository;
import com.nlb.service.models.BatchItem;
import com.nlb.service.models.BatchTransferRequest;
import com.nlb.service.models.BatchTransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultTransferBatchServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepo;
    @Mock
    private TransactionRepository transactionRepo;
    @Mock
    private IntegrationFailureRepository failureRepo;
    @Mock
    private AccountRepository accountRepo;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DefaultTransferBatchService transferService;

    @Captor
    private ArgumentCaptor<PaymentOrder> paymentOrderCaptor;
    @Captor
    private ArgumentCaptor<List<Transaction>> transactionsCaptor;
    @Captor
    private ArgumentCaptor<List<Account>> accountsCaptor;
    @Captor
    private ArgumentCaptor<IntegrationFailure> failureCaptor;

    private UUID userId;
    private UUID sourceAccountId;
    private UUID destAccountId;
    private User mockUser;
    private Account mockSourceAccount;
    private Account mockDestAccount;
    private BatchTransferRequest mockRequest;

    @BeforeEach
    void setUp() {
        transferService = new DefaultTransferBatchService(
                paymentOrderRepo, transactionRepo, failureRepo, accountRepo, objectMapper
        );

        userId = UUID.randomUUID();
        sourceAccountId = UUID.randomUUID();
        destAccountId = UUID.randomUUID();

        mockUser = new User(userId, "user@example.com", "Test User", UserStatus.ACTIVE);

        mockSourceAccount = Account.builder()
                .id(sourceAccountId)
                .owner(mockUser)
                .balanceCents(10000L)
                .currency(Currency.EUR)
                .status(AccountStatus.ACTIVE)
                .build();

        mockDestAccount = Account.builder()
                .id(destAccountId)
                .owner(new User())
                .balanceCents(0L)
                .currency(Currency.EUR)
                .status(AccountStatus.ACTIVE)
                .build();

        BatchItem item = new BatchItem(destAccountId, new BigDecimal("10.00"));
        mockRequest = new BatchTransferRequest(
                UUID.randomUUID().toString(),
                userId,
                sourceAccountId,
                List.of(item)
        );
    }

    /**
     * Testira "happy path" - uspešan transfer
     */
    @Test
    void executeBatchTransfer_shouldSucceed_onHappyPath() throws Exception {
        when(paymentOrderRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentOrderRepo.saveAndFlush(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepo.findAllByIdInAndLock(anyList()))
                .thenReturn(List.of(mockSourceAccount, mockDestAccount));
        when(accountRepo.saveAll(anyList())).thenReturn(null);
        when(transactionRepo.saveAll(anyList())).thenReturn(null);
        when(paymentOrderRepo.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchTransferResponse response = transferService.executeBatchTransfer(mockRequest);

        assertThat(response.status()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(response.message()).isEqualTo("Transfer successful");

        verify(paymentOrderRepo, times(1)).save(paymentOrderCaptor.capture());
        PaymentOrder capturedOrder = paymentOrderCaptor.getValue();
        assertThat(capturedOrder.getStatus()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(capturedOrder.getItems().getFirst().getStatus()).isEqualTo(PaymentOrderItemStatus.SUCCESS);

        verify(accountRepo, times(1)).saveAll(accountsCaptor.capture());
        List<Account> updatedAccounts = accountsCaptor.getValue();
        assertThat(updatedAccounts).hasSize(2);
        assertThat(updatedAccounts.get(0).getId()).isEqualTo(sourceAccountId);
        assertThat(updatedAccounts.get(0).getBalanceCents()).isEqualTo(9000L); // 100 - 10
        assertThat(updatedAccounts.get(1).getId()).isEqualTo(destAccountId);
        assertThat(updatedAccounts.get(1).getBalanceCents()).isEqualTo(1000L); // 0 + 10

        verify(transactionRepo, times(1)).saveAll(transactionsCaptor.capture());
        List<Transaction> transactions = transactionsCaptor.getValue();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getAmountCents()).isEqualTo(1000L);
        assertThat(transactions.getFirst().getSourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(transactions.getFirst().getDestinationAccountId()).isEqualTo(destAccountId);
    }

    /**
     * Testira proveru idempotentnosti (brzi put)
     */
    @Test
    void executeBatchTransfer_shouldReturnExistingResult_whenIdempotencyKeyExists() {
        PaymentOrder existingOrder = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .status(PaymentOrderStatus.COMPLETED)
                .build();
        when(paymentOrderRepo.findByIdempotencyKey(mockRequest.idempotencyKey()))
                .thenReturn(Optional.of(existingOrder));

        BatchTransferResponse response = transferService.executeBatchTransfer(mockRequest);

        assertThat(response.status()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(response.message()).isEqualTo("Request already processed");
        assertThat(response.paymentOrderId()).isEqualTo(existingOrder.getId());

        verify(paymentOrderRepo, never()).saveAndFlush(any());
        verify(accountRepo, never()).findAllByIdInAndLock(anyList());
    }

    /**
     * Testira poslovnu grešku: Nedovoljno sredstava
     */
    @Test
    void executeBatchTransfer_shouldFail_whenInsufficientFunds() {
        mockSourceAccount.setBalanceCents(500L);

        when(paymentOrderRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentOrderRepo.saveAndFlush(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepo.findAllByIdInAndLock(anyList()))
                .thenReturn(List.of(mockSourceAccount, mockDestAccount));
        when(paymentOrderRepo.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchTransferResponse response = transferService.executeBatchTransfer(mockRequest);

        assertThat(response.status()).isEqualTo(PaymentOrderStatus.FAILED);
        assertThat(response.message()).isEqualTo("Insufficient funds");

        verify(paymentOrderRepo, times(1)).save(paymentOrderCaptor.capture());
        PaymentOrder capturedOrder = paymentOrderCaptor.getValue();
        assertThat(capturedOrder.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
        assertThat(capturedOrder.getItems().getFirst().getStatus()).isEqualTo(PaymentOrderItemStatus.FAILED);
        assertThat(capturedOrder.getItems().getFirst().getFailureReason()).isEqualTo("Insufficient funds");

        verify(accountRepo, never()).saveAll(anyList());
        verify(transactionRepo, never()).saveAll(anyList());
    }

    /**
     * Testira poslovnu grešku: Vlasništvo nad nalogom
     */
    @Test
    void executeBatchTransfer_shouldFail_whenUserDoesNotOwnSourceAccount() {
        BatchTransferRequest badRequest = new BatchTransferRequest(
                mockRequest.idempotencyKey(),
                UUID.randomUUID(),
                mockRequest.sourceAccountId(),
                mockRequest.items()
        );

        when(paymentOrderRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentOrderRepo.saveAndFlush(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepo.findAllByIdInAndLock(anyList()))
                .thenReturn(List.of(mockSourceAccount, mockDestAccount));
        when(paymentOrderRepo.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        BatchTransferResponse response = transferService.executeBatchTransfer(badRequest);

        assertThat(response.status()).isEqualTo(PaymentOrderStatus.FAILED);
        assertThat(response.message()).isEqualTo("User does not own the source account");
        verify(accountRepo, never()).saveAll(anyList());
        verify(transactionRepo, never()).saveAll(anyList());
    }

    /**
     * Testira sistemsku grešku (npr. DB pukne)
     */
    @Test
    void executeBatchTransfer_shouldRollbackAndLog_whenSystemFailureOccurs() throws Exception {
        when(paymentOrderRepo.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(paymentOrderRepo.saveAndFlush(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepo.findAllByIdInAndLock(anyList()))
                .thenReturn(List.of(mockSourceAccount, mockDestAccount));

        RuntimeException dbException = new RuntimeException("DB Connection Timeout");
        when(accountRepo.saveAll(anyList())).thenThrow(dbException);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(failureRepo.save(any(IntegrationFailure.class))).thenReturn(null);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            transferService.executeBatchTransfer(mockRequest);
        });
        assertThat(thrown.getMessage()).contains("System error processing transfer");

        verify(failureRepo, times(1)).save(failureCaptor.capture());
        IntegrationFailure capturedFailure = failureCaptor.getValue();
        assertThat(capturedFailure.getContext()).isEqualTo("TRANSFER_BATCH_SERVICE");
        assertThat(capturedFailure.getMessage()).isEqualTo("DB Connection Timeout");
        assertThat(capturedFailure.getPayload()).isEqualTo("{}");

        verify(paymentOrderRepo, never()).save(any(PaymentOrder.class));
        verify(transactionRepo, never()).saveAll(anyList());
    }

    /**
     * Testira "race condition" kod idempotentnosti
     */
    @Test
    void executeBatchTransfer_shouldHandleIdempotencyRaceCondition() {
        PaymentOrder racedOrder = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .status(PaymentOrderStatus.COMPLETED) // Drugi thread je već završio
                .build();

        when(paymentOrderRepo.findByIdempotencyKey(mockRequest.idempotencyKey()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedOrder));

        when(paymentOrderRepo.saveAndFlush(any(PaymentOrder.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        BatchTransferResponse response = transferService.executeBatchTransfer(mockRequest);

        assertThat(response.status()).isEqualTo(PaymentOrderStatus.COMPLETED);
        assertThat(response.message()).isEqualTo("Concurrent request processed");
        assertThat(response.paymentOrderId()).isEqualTo(racedOrder.getId());
    }
}
