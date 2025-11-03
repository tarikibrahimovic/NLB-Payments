package com.nlb.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlb.dto.auth.LoginRequest;
import com.nlb.dto.auth.RegisterRequest;
import com.nlb.dto.auth.RegisterResponse;
import com.nlb.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = NlbPaymentApplication.class)
@AutoConfigureMockMvc
@Testcontainers
@Transactional
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        var request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.accountId").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty());

        var user = userRepository.findByEmail("test@example.com");
        assertThat(user).isPresent();
        assertThat(user.get().getFullName()).isEqualTo("Test User");
    }

    @Test
    void shouldFailRegistrationForDuplicateEmail() throws Exception {
        registerUser("duplicate@example.com", "User One");

        var duplicateRequest = new RegisterRequest();
        duplicateRequest.setEmail("duplicate@example.com");
        duplicateRequest.setFullName("User Two");

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void shouldFailRegistrationForInvalidData() throws Exception {
        var request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setFullName("");

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        registerUser("login-user@example.com", "Login User");

        var loginRequest = new LoginRequest();
        loginRequest.setEmail("login-user@example.com");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void shouldFailLoginForNonExistentUser() throws Exception {
        var loginRequest = new LoginRequest();
        loginRequest.setEmail("ghost@example.com");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldLoginByIdSuccessfully() throws Exception {
        var registeredUser = registerUser("login-by-id@example.com", "ID User");
        UUID userId = UUID.fromString(registeredUser.getUserId());

        mvc.perform(post("/auth/login-by-id/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void shouldFailLoginByIdForNonExistentUser() throws Exception {
        UUID randomId = UUID.randomUUID();

        mvc.perform(post("/auth/login-by-id/" + randomId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"));
    }

    private RegisterResponse registerUser(String email, String fullName) throws Exception {
        var req = new RegisterRequest();
        req.setEmail(email);
        req.setFullName(fullName);

        MvcResult result = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String jsonResponse = result.getResponse().getContentAsString();
        return objectMapper.readValue(jsonResponse, RegisterResponse.class);
    }
}
