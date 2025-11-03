package com.nlb.service;

import com.nlb.domain.*;
import com.nlb.interfaces.RegistrationService;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private JwtEncoder jwtEncoder;

    @InjectMocks
    private DefaultRegistrationService registrationService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        mockJwt = Jwt.withTokenValue("mock.token.string")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .build();
    }

    @Test
    void register_shouldCreateUserAndAccount_andReturnResult() {
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationService.RegistrationResult result = registrationService.register(
                "novi.korisnik@example.com",
                "Novi Korisnik",
                8
        );

        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("mock.token.string");
        assertThat(result.userId()).isNotNull();
        assertThat(result.accountId()).isNotNull();

        verify(userRepository, times(1)).saveAndFlush(userCaptor.capture());
        verify(accountRepository, times(1)).save(accountCaptor.capture());

        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getEmail()).isEqualTo("novi.korisnik@example.com");
        assertThat(capturedUser.getFullName()).isEqualTo("Novi Korisnik");
        assertThat(capturedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        Account capturedAccount = accountCaptor.getValue();
        assertThat(capturedAccount.getOwner()).isEqualTo(capturedUser);
        assertThat(capturedAccount.getBalanceCents()).isZero();
        assertThat(capturedAccount.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(capturedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void register_shouldThrowIllegalStateException_whenEmailExists() {
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Email already registered"));

        assertThrows(IllegalStateException.class, () -> {
            registrationService.register("postojeci.email@example.com", "Neko Drugi", 8);
        }, "Email already registered");

        verify(accountRepository, never()).save(any());
        verify(jwtEncoder, never()).encode(any());
    }
}
