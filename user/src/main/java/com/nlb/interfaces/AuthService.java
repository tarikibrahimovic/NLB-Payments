package com.nlb.interfaces;

import java.util.UUID;

public interface AuthService {

    String issueTokenForEmail(String email, long hours);
    String issueTokenForUserId(UUID userId, long hours);
}
