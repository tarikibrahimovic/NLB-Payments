package com.nlb.controller;

import com.nlb.dto.auth.LoginRequest;
import com.nlb.dto.auth.RegisterRequest;
import com.nlb.dto.auth.RegisterResponse;
import com.nlb.interfaces.AuthService;
import com.nlb.interfaces.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

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
    public Map<String, String> login(@RequestBody @Validated LoginRequest req) {
        String token = auth.issueTokenForEmail(req.getEmail(), 8);
        return Map.of("token", token);
    }

    @PostMapping("/login-by-id/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> loginById(@PathVariable UUID userId,
                                         @RequestParam(defaultValue = "8") long hours) {
        String token = auth.issueTokenForUserId(userId, hours);
        return Map.of("token", token);
    }

}