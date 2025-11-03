package com.nlb.service;

import com.nlb.domain.*;
import com.nlb.interfaces.RegistrationService;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class DefaultRegistrationService implements RegistrationService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final JwtEncoder jwtEncoder;

    private final String ISSUER = "nlb-dev";

    @Override
    @Transactional
    public RegistrationResult register(String email, String fullName, long tokenHours) {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Email already registered");
        }

        var acc = new Account();
        acc.setId(UUID.randomUUID());
        acc.setOwner(user);
        acc.setBalanceCents(0L);
        acc.setCurrency(Currency.EUR);
        acc.setStatus(AccountStatus.ACTIVE);
        accounts.save(acc);

        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(tokenHours)))
                .subject(user.getId().toString())
                .build();
        var token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return new RegistrationResult(user.getId(), acc.getId(), token);
    }
}