package com.nlb.interfaces;


import com.nlb.domain.IntegrationFailure;
import com.nlb.domain.PaymentOrder;
import com.nlb.domain.Transaction;

import java.util.List;
import java.util.UUID;

public interface ReportService {

    List<PaymentOrder> getPaymentOrdersForUser(UUID userId);

    PaymentOrder getPaymentOrderDetails(UUID userId, UUID orderId);

    List<Transaction> getTransactionsForAccount(UUID userId, UUID accountId);

    List<IntegrationFailure> getAllIntegrationFailures();
}
