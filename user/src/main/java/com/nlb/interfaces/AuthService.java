package com.nlb.interfaces;

import java.util.UUID;

public interface AuthService {

    public String issueTokenForEmail(String email, long hours);
    public String issueTokenForUserId(UUID userId, long hours);
}
