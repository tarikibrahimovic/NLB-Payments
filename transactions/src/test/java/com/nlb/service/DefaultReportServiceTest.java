package com.nlb.service;

import com.nlb.domain.*;
import com.nlb.exception.BusinessValidationException;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.IntegrationFailureRepository;
import com.nlb.repository.PaymentOrderRepository;
import com.nlb.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultReportServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepo;
    @Mock
    private TransactionRepository transactionRepo;
    @Mock
    private IntegrationFailureRepository failureRepo;
    @Mock
    private AccountRepository accountRepo;

    @InjectMocks
    private DefaultReportService reportService;

    private UUID userId;
    private UUID accountId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    void getPaymentOrdersForUser_shouldReturnOrderList() {
        List<PaymentOrder> mockOrders = List.of(new PaymentOrder(), new PaymentOrder());
        when(paymentOrderRepo.findByInitiatedByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(mockOrders);

        List<PaymentOrder> result = reportService.getPaymentOrdersForUser(userId);

        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(mockOrders);
        verify(paymentOrderRepo).findByInitiatedByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void getPaymentOrderDetails_shouldReturnOrder_whenUserIsOwner() {
        PaymentOrder mockOrder = new PaymentOrder();
        mockOrder.setItems(new ArrayList<>());
        when(paymentOrderRepo.findByIdAndInitiatedByUserId(orderId, userId))
                .thenReturn(Optional.of(mockOrder));

        PaymentOrder result = reportService.getPaymentOrderDetails(userId, orderId);

        assertThat(result).isEqualTo(mockOrder);
        verify(paymentOrderRepo).findByIdAndInitiatedByUserId(orderId, userId);
    }

    @Test
    void getPaymentOrderDetails_shouldThrowException_whenOrderNotFound() {
        when(paymentOrderRepo.findByIdAndInitiatedByUserId(orderId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getPaymentOrderDetails(userId, orderId))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Payment order not found or user does not have access");
    }

    @Test
    void getTransactionsForAccount_shouldReturnTransactionList_whenUserIsOwner() {
        User mockUser = new User();
        mockUser.setId(userId);
        Account mockAccount = Account.builder().owner(mockUser).build();
        List<Transaction> mockTransactions = List.of(new Transaction());

        when(accountRepo.findById(accountId)).thenReturn(Optional.of(mockAccount));
        when(transactionRepo.findByAccountId(accountId)).thenReturn(mockTransactions);

        List<Transaction> result = reportService.getTransactionsForAccount(userId, accountId);

        assertThat(result).isEqualTo(mockTransactions);
        verify(accountRepo).findById(accountId);
        verify(transactionRepo).findByAccountId(accountId);
    }

    @Test
    void getTransactionsForAccount_shouldThrowException_whenUserIsNotOwner() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        Account mockAccount = Account.builder().owner(otherUser).build();

        when(accountRepo.findById(accountId)).thenReturn(Optional.of(mockAccount));

        assertThatThrownBy(() -> reportService.getTransactionsForAccount(userId, accountId))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Account not found or user does not have access");

        verify(transactionRepo, never()).findByAccountId(any());
    }

    @Test
    void getAllIntegrationFailures_shouldReturnFailureList() {
        List<IntegrationFailure> mockFailures = List.of(new IntegrationFailure());
        when(failureRepo.findAll()).thenReturn(mockFailures);

        List<IntegrationFailure> result = reportService.getAllIntegrationFailures();

        assertThat(result).isEqualTo(mockFailures);
        verify(failureRepo).findAll();
    }
}
