package com.nlb.service.models;

import com.nlb.domain.PaymentOrderStatus;
import java.util.UUID;

public record BatchTransferResponse(
        UUID paymentOrderId,
        PaymentOrderStatus status,
        String message
) {}