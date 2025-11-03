package com.nlb.service;

import com.nlb.domain.Account;
import com.nlb.domain.User;
import com.nlb.domain.UserStatus;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.UserRepository;
import com.nlb.service.model.LoginResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private JwtEncoder jwtEncoder;

    @InjectMocks
    private DefaultAuthService defaultAuthService;

    private User mockUser;
    private Jwt mockJwt;
    private List<Account> mockAccounts;
    private UUID accountId1;
    private UUID accountId2;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        mockUser = new User(userId, "test@example.com", "Test User", UserStatus.ACTIVE);

        mockJwt = Jwt.withTokenValue("mock.token.string")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .build();

        accountId1 = UUID.randomUUID();
        accountId2 = UUID.randomUUID();
        mockAccounts = List.of(
                Account.builder().id(accountId1).build(),
                Account.builder().id(accountId2).build()
        );
    }

    @Test
    void issueTokenForEmail_shouldReturnResult_whenEmailExists() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(accountRepository.findByOwnerId(mockUser.getId())).thenReturn(mockAccounts);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        LoginResult result = defaultAuthService.issueTokenForEmail("test@example.com", 8);

        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("mock.token.string");
        assertThat(result.accountIds()).hasSize(2);
        assertThat(result.accountIds()).containsExactlyInAnyOrder(accountId1, accountId2);

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(accountRepository, times(1)).findByOwnerId(mockUser.getId());
    }

    @Test
    void issueTokenForEmail_shouldThrowException_whenEmailNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            defaultAuthService.issueTokenForEmail("ghost@example.com", 8);
        });

        verify(jwtEncoder, never()).encode(any());
        verify(accountRepository, never()).findByOwnerId(any());
    }

    @Test
    void issueTokenForUserId_shouldReturnResult_whenIdExists() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(mockUser));
        when(accountRepository.findByOwnerId(mockUser.getId())).thenReturn(mockAccounts);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);

        LoginResult result = defaultAuthService.issueTokenForUserId(mockUser.getId(), 8);

        assertThat(result).isNotNull();
        assertThat(result.token()).isEqualTo("mock.token.string");
        assertThat(result.accountIds()).hasSize(2);

        verify(userRepository, times(1)).findById(mockUser.getId());
        verify(accountRepository, times(1)).findByOwnerId(mockUser.getId());
    }

    @Test
    void issueTokenForUserId_shouldThrowException_whenIdNotFound() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        UUID randomId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> {
            defaultAuthService.issueTokenForUserId(randomId, 8);
        });

        verify(jwtEncoder, never()).encode(any());
        verify(accountRepository, never()).findByOwnerId(any());
    }
}

