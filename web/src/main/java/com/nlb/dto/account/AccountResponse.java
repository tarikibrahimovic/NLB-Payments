package com.nlb.dto.account;

import com.nlb.domain.Account;
import com.nlb.domain.AccountStatus;
import com.nlb.domain.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private UUID accountId;
    private UUID ownerId;
    private String balance;
    private Currency currency;
    private AccountStatus status;

    public static String formatBalance(long balanceCents) {
        return new BigDecimal(balanceCents)
                .divide(new BigDecimal("100"))
                .setScale(2)
                .toString();
    }

    public static AccountResponse fromEntity(Account account) {
        return AccountResponse.builder()
                .accountId(account.getId())
                .ownerId(account.getOwner().getId())
                .balance(formatBalance(account.getBalanceCents()))
                .currency(account.getCurrency())
                .status(account.getStatus())
                .build();
    }
}