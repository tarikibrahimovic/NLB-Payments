package com.nlb.dto.report;

import com.nlb.domain.Currency;
import com.nlb.domain.Transaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Data
@Builder
public class TransactionReport {
    private UUID transactionId;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private BigDecimal amount;
    private Currency currency;
    private Instant createdAt;
    private UUID paymentOrderId;

    public static TransactionReport fromEntity(Transaction t) {
        return TransactionReport.builder()
                .transactionId(t.getId())
                .sourceAccountId(t.getSourceAccountId())
                .destinationAccountId(t.getDestinationAccountId())
                .amount(new BigDecimal(t.getAmountCents()).divide(new BigDecimal("100")))
                .currency(t.getCurrency())
                .createdAt(t.getCreatedAt())
                .paymentOrderId(t.getPaymentOrderId())
                .build();
    }
}
