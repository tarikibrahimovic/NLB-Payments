package com.nlb.service;

import com.nlb.domain.IntegrationFailure;
import com.nlb.domain.PaymentOrder;
import com.nlb.domain.Transaction;
import com.nlb.exception.BusinessValidationException;

import com.nlb.interfaces.ReportService;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.IntegrationFailureRepository;
import com.nlb.repository.PaymentOrderRepository;
import com.nlb.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultReportService implements ReportService {

    private final PaymentOrderRepository paymentOrderRepo;
    private final TransactionRepository transactionRepo;
    private final IntegrationFailureRepository failureRepo;
    private final AccountRepository accountRepo;

    @Override
    public List<PaymentOrder> getPaymentOrdersForUser(UUID userId) {
        return paymentOrderRepo.findByInitiatedByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional
    public PaymentOrder getPaymentOrderDetails(UUID userId, UUID orderId) {
        PaymentOrder order = paymentOrderRepo.findByIdAndInitiatedByUserId(orderId, userId)
                .orElseThrow(() -> new BusinessValidationException("Payment order not found or user does not have access"));

        order.getItems().size();
        return order;
    }

    @Override
    public List<Transaction> getTransactionsForAccount(UUID userId, UUID accountId) {
        accountRepo.findById(accountId)
                .filter(acc -> acc.getOwner().getId().equals(userId))
                .orElseThrow(() -> new BusinessValidationException("Account not found or user does not have access"));

        return transactionRepo.findByAccountId(accountId);
    }

    @Override
    public List<IntegrationFailure> getAllIntegrationFailures() {
        return failureRepo.findAll();
    }
}
