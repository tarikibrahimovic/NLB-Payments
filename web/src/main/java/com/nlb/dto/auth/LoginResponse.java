package com.nlb.dto.auth;

import java.util.List;

public record LoginResponse(
        String token,
        List<String> accountIds
) {}