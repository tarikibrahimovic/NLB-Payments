package com.nlb.dto.report;

import com.nlb.domain.Currency;
import com.nlb.domain.PaymentOrder;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
public class PaymentOrderReport {
    private UUID paymentOrderId;
    private String idempotencyKey;
    private UUID sourceAccountId;
    private BigDecimal totalAmount;
    private Currency currency;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<PaymentOrderItemReport> items;

    public static PaymentOrderReport fromEntitySummary(PaymentOrder order) {
        return PaymentOrderReport.builder()
                .paymentOrderId(order.getId())
                .idempotencyKey(order.getIdempotencyKey())
                .sourceAccountId(order.getSourceAccountId())
                .totalAmount(new BigDecimal(order.getTotalAmountCents()).divide(new BigDecimal("100")))
                .currency(order.getCurrency())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(null)
                .build();
    }

    public static PaymentOrderReport fromEntityDetails(PaymentOrder order) {
        return PaymentOrderReport.builder()
                .paymentOrderId(order.getId())
                .idempotencyKey(order.getIdempotencyKey())
                .sourceAccountId(order.getSourceAccountId())
                .totalAmount(new BigDecimal(order.getTotalAmountCents()).divide(new BigDecimal("100")))
                .currency(order.getCurrency())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(order.getItems().stream()
                        .map(PaymentOrderItemReport::fromEntity)
                        .collect(Collectors.toList()))
                .build();
    }
}
