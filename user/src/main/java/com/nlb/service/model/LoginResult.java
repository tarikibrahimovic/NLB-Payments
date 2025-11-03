package com.nlb.service.model;

import java.util.List;
import java.util.UUID;

public record LoginResult(
        String token,
        List<UUID> accountIds
) {}