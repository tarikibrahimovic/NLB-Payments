package com.nlb.service;

import com.nlb.domain.Account;
import com.nlb.domain.AccountStatus;
import com.nlb.domain.Currency;
import com.nlb.domain.User;
import com.nlb.exception.BusinessValidationException;
import com.nlb.interfaces.AccountService;
import com.nlb.repository.AccountRepository;
import com.nlb.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultAccountService implements AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final BigDecimal DECIMAL_MULTIPLIER = new BigDecimal("100");

    @Override
    @Transactional
    public Account createAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessValidationException("User not found"));

        Account account = Account.builder()
                .id(UUID.randomUUID())
                .owner(user)
                .balanceCents(0L)
                .currency(Currency.EUR)
                .status(AccountStatus.ACTIVE)
                .build();

        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public Account deposit(UUID userId, UUID accountId, BigDecimal amount) {
        Account account = getAccountAndVerifyOwnership(userId, accountId);

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessValidationException("Account is not ACTIVE");
        }

        if (!Currency.EUR.equals(account.getCurrency())) {
            throw new BusinessValidationException("Deposits are only allowed to EUR accounts");
        }

        long amountCents = amount.multiply(DECIMAL_MULTIPLIER).longValueExact();
        account.setBalanceCents(account.getBalanceCents() + amountCents);

        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public Account withdraw(UUID userId, UUID accountId, BigDecimal amount) {
        Account account = getAccountAndVerifyOwnership(userId, accountId);

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessValidationException("Account is not ACTIVE");
        }

        long amountCents = amount.multiply(DECIMAL_MULTIPLIER).longValueExact();

        if (account.getBalanceCents() < amountCents) {
            throw new BusinessValidationException("Insufficient funds");
        }

        account.setBalanceCents(account.getBalanceCents() - amountCents);

        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public Account deactivateAccount(UUID userId, UUID accountId) {
        Account account = getAccountAndVerifyOwnership(userId, accountId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessValidationException("Account is already closed");
        }

        if (account.getBalanceCents() > 0) {
            throw new BusinessValidationException("Cannot deactivate account with a positive balance. Please transfer funds first.");
        }

        account.setStatus(AccountStatus.CLOSED);
        return accountRepository.save(account);
    }

    @Override
    public List<Account> getAccountsByUserId(UUID userId) {
        return accountRepository.findByOwnerId(userId);
    }

    private Account getAccountAndVerifyOwnership(UUID userId, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessValidationException("Account not found: " + accountId));

        if (!account.getOwner().getId().equals(userId)) {
            throw new BusinessValidationException("User does not own this account");
        }

        return account;
    }
}