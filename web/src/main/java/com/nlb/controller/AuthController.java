package com.nlb.controller;

import com.nlb.interfaces.AuthService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
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

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> login(@RequestBody @Validated LoginRequest req) {
        String token = auth.issueTokenForEmail(req.getEmail(), 8);
        return Map.of("token", token);
    }

    // 2) Alternativni login po userId (korisno za testove)
    @PostMapping("/login-by-id/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> loginById(@PathVariable UUID userId,
                                         @RequestParam(defaultValue = "8") long hours) {
        String token = auth.issueTokenForUserId(userId, hours);
        return Map.of("token", token);
    }

    @Data
    public static class LoginRequest {
        @Email @NotBlank
        private String email;
    }
}