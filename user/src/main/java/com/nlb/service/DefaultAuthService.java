package com.nlb.service;

import com.nlb.interfaces.AuthService;
import com.nlb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultAuthService implements AuthService {
    private final UserRepository users;
    private final JwtEncoder jwtEncoder;

    public String issueTokenForEmail(String email, long hours) {
        var user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User with that email not found"));

        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("nlb-dev")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(hours)))
                .subject(user.getId().toString())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public String issueTokenForUserId(UUID userId, long hours) {
        var user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("nlb-dev")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(hours)))
                .subject(user.getId().toString())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}