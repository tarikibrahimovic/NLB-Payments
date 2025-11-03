package com.nlb.dto.report;

import com.nlb.domain.PaymentOrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class PaymentOrderItemReport {
    private UUID itemId;
    private UUID destinationAccountId;
    private BigDecimal amount;
    private String status;
    private String failureReason;

    public static PaymentOrderItemReport fromEntity(PaymentOrderItem item) {
        return PaymentOrderItemReport.builder()
                .itemId(item.getId())
                .destinationAccountId(item.getDestinationAccountId())
                .amount(new BigDecimal(item.getAmountCents()).divide(new BigDecimal("100")))
                .status(item.getStatus().name())
                .failureReason(item.getFailureReason())
                .build();
    }
}
