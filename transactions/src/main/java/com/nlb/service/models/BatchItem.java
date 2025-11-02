package com.nlb.service.models;

import java.math.BigDecimal;
import java.util.UUID;

public record BatchItem(
        UUID destinationAccountId,
        BigDecimal amount
) {}