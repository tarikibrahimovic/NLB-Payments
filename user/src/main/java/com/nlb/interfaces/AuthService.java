package com.nlb.interfaces;

import com.nlb.service.model.LoginResult;

import java.util.UUID;

public interface AuthService {

    LoginResult issueTokenForEmail(String email, long hours);
    LoginResult issueTokenForUserId(UUID userId, long hours);
}
