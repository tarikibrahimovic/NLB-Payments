package com.nlb.service.models;

import java.util.List;
import java.util.UUID;

public record BatchTransferRequest(
        String idempotencyKey,
        UUID initiatedByUserId,
        UUID sourceAccountId,
        List<BatchItem> items
) {}