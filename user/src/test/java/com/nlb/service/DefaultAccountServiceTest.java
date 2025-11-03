package com.nlb.service;


import com.nlb.domain.Account;
import com.nlb.domain.AccountStatus;
import com.nlb.domain.Currency;
import com.nlb.domain.User;
import com.nlb.exception.BusinessValidationException;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultAccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DefaultAccountService accountService;

    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private User mockUser;
    private Account mockAccount;
    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();

        mockUser = new User();
        mockUser.setId(userId);
        mockUser.setEmail("test@user.com");

        mockAccount = Account.builder()
                .id(accountId)
                .owner(mockUser)
                .balanceCents(10000L)
                .currency(Currency.EUR)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void getAccountAndVerifyOwnership_shouldFail_whenAccountNotFound() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deposit(userId, accountId, BigDecimal.TEN))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void getAccountAndVerifyOwnership_shouldFail_whenUserDoesNotOwnAccount() {
        UUID otherUserId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));

        assertThatThrownBy(() -> accountService.deposit(otherUserId, accountId, BigDecimal.TEN))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("User does not own this account");
    }

    @Test
    void createAccount_shouldCreateAndSaveActiveEurAccount() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        Account newAccount = accountService.createAccount(userId);

        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();

        assertThat(newAccount).isNotNull();
        assertThat(savedAccount.getOwner()).isEqualTo(mockUser);
        assertThat(savedAccount.getBalanceCents()).isEqualTo(0L);
        assertThat(savedAccount.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void createAccount_shouldFail_whenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.createAccount(userId))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("User not found");
    }

    @Test
    void deposit_shouldIncreaseBalance_whenAccountIsActive() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(mockAccount);

        BigDecimal depositAmount = new BigDecimal("50.50");
        accountService.deposit(userId, accountId, depositAmount);

        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getBalanceCents()).isEqualTo(15050L);
    }

    @Test
    void deposit_shouldFail_whenAccountIsNotActive() {
        mockAccount.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));

        assertThatThrownBy(() -> accountService.deposit(userId, accountId, BigDecimal.TEN))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Account is not ACTIVE");
    }

    @Test
    void withdraw_shouldDecreaseBalance_whenSufficientFunds() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(mockAccount);

        BigDecimal withdrawAmount = new BigDecimal("30.00");
        accountService.withdraw(userId, accountId, withdrawAmount);

        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getBalanceCents()).isEqualTo(7000L);
    }

    @Test
    void withdraw_shouldFail_whenInsufficientFunds() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));

        BigDecimal withdrawAmount = new BigDecimal("200.00");

        assertThatThrownBy(() -> accountService.withdraw(userId, accountId, withdrawAmount))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Insufficient funds");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void withdraw_shouldFail_whenAccountIsNotActive() {
        mockAccount.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));

        assertThatThrownBy(() -> accountService.withdraw(userId, accountId, BigDecimal.TEN))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Account is not ACTIVE");
    }

    @Test
    void deactivateAccount_shouldSetStatusToClosed_whenBalanceIsZero() {
        mockAccount.setBalanceCents(0L);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(mockAccount);

        accountService.deactivateAccount(userId, accountId);

        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();

        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void deactivateAccount_shouldFail_whenBalanceIsPositive() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));

        assertThatThrownBy(() -> accountService.deactivateAccount(userId, accountId))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Cannot deactivate account with a positive balance. Please transfer funds first.");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void deactivateAccount_shouldFail_whenAlreadyClosed() {
        mockAccount.setBalanceCents(0L);
        mockAccount.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));

        assertThatThrownBy(() -> accountService.deactivateAccount(userId, accountId))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Account is already closed");

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void getAccountsByUserId_shouldReturnAccountList() {
        when(accountRepository.findByOwnerId(userId)).thenReturn(List.of(mockAccount));

        List<Account> accounts = accountService.getAccountsByUserId(userId);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.getFirst()).isEqualTo(mockAccount);
    }
}
