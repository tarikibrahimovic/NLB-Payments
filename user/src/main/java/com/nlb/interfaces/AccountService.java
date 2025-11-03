package com.nlb.interfaces;

import com.nlb.domain.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AccountService {

    Account createAccount(UUID userId);

    Account deposit(UUID userId, UUID accountId, BigDecimal amount);

    Account deactivateAccount(UUID userId, UUID accountId);

    List<Account> getAccountsByUserId(UUID userId);

    Account withdraw(UUID userId, UUID accountId, BigDecimal amount);
}
