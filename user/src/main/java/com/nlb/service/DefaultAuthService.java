package com.nlb.service;

import com.nlb.domain.Account;
import com.nlb.interfaces.AuthService;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.UserRepository;
import com.nlb.service.model.LoginResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultAuthService implements AuthService {

    private final UserRepository users;
    private final AccountRepository accountRepository;
    private final JwtEncoder jwtEncoder;

    @Override
    @Transactional(readOnly = true)
    public LoginResult issueTokenForEmail(String email, long hours) {
        var user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User with that email not found"));

        List<UUID> accountIds = accountRepository.findByOwnerId(user.getId())
                .stream()
                .map(Account::getId)
                .collect(Collectors.toList());

        String token = tokenFor(user.getId(), hours);
        return new LoginResult(token, accountIds);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult issueTokenForUserId(UUID userId, long hours) {
        users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<UUID> accountIds = accountRepository.findByOwnerId(userId)
                .stream()
                .map(Account::getId)
                .collect(Collectors.toList());

        String token = tokenFor(userId, hours);
        return new LoginResult(token, accountIds);
    }

    private String tokenFor(UUID userId, long hours) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("nlb-dev")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(hours)))
                .subject(userId.toString())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}