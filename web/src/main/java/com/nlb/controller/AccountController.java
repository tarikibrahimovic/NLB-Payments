package com.nlb.controller;

import com.nlb.dto.account.AccountResponse;
import com.nlb.dto.account.DepositRequest;
import com.nlb.dto.account.WithdrawRequest;
import com.nlb.interfaces.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountResponse> getMyAccounts(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return accountService.getAccountsByUserId(userId).stream()
                .map(AccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        var newAccount = accountService.createAccount(userId);
        return AccountResponse.fromEntity(newAccount);
    }

    @PostMapping("/{accountId}/deposit")
    public AccountResponse depositFunds(
            @PathVariable UUID accountId,
            @RequestBody @Valid DepositRequest request,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        var updatedAccount = accountService.deposit(userId, accountId, request.getAmount());
        return AccountResponse.fromEntity(updatedAccount);
    }

    @PostMapping("/{accountId}/withdraw")
    public AccountResponse withdrawFunds(
            @PathVariable UUID accountId,
            @RequestBody @Valid WithdrawRequest request,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        var updatedAccount = accountService.withdraw(userId, accountId, request.getAmount());
        return AccountResponse.fromEntity(updatedAccount);
    }

    @DeleteMapping("/{accountId}")
    public AccountResponse deactivateAccount(
            @PathVariable UUID accountId,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        var updatedAccount = accountService.deactivateAccount(userId, accountId);
        return AccountResponse.fromEntity(updatedAccount);
    }
}