package com.nlb.controller;

import com.nlb.dto.auth.LoginRequest;
import com.nlb.dto.auth.LoginResponse;
import com.nlb.dto.auth.RegisterRequest;
import com.nlb.dto.auth.RegisterResponse;
import com.nlb.interfaces.AuthService;
import com.nlb.interfaces.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService auth;
    private final RegistrationService registrationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@RequestBody @Valid RegisterRequest req) {
        var res = registrationService.register(req.getEmail(), req.getFullName(), 8);
        return new RegisterResponse(res.userId().toString(), res.accountId().toString(), res.token());
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@RequestBody @Valid LoginRequest req) {
        var result = auth.issueTokenForEmail(req.getEmail(), 8);

        List<String> accountIdStrings = result.accountIds().stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

        return new LoginResponse(result.token(), accountIdStrings);
    }

    @PostMapping("/login-by-id/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse loginById(@PathVariable UUID userId,
                                   @RequestParam(defaultValue = "8") long hours) {
        var result = auth.issueTokenForUserId(userId, hours);

        List<String> accountIdStrings = result.accountIds().stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

        return new LoginResponse(result.token(), accountIdStrings);
    }

}