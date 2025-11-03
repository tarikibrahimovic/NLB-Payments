package com.nlb.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlb.dto.auth.RegisterRequest;
import com.nlb.dto.auth.RegisterResponse;
import com.nlb.dto.transaction.TransferBatchRequest;
import com.nlb.dto.transaction.TransferBatchItemRequest;
import com.nlb.domain.Account;
import com.nlb.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = NlbPaymentApplication.class)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
public class TransferIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    private RegisterResponse userA;
    private RegisterResponse userB;

    @BeforeEach
    void setUp() throws Exception {
        userA = registerUser("userA@example.com", "User A");
        userB = registerUser("userB@example.com", "User B");

        Account accountA = accountRepository.findById(UUID.fromString(userA.getAccountId())).get();
        accountA.setBalanceCents(10000L);
        accountRepository.save(accountA);
    }

    @Test
    void shouldExecuteBatchTransferSuccessfully() throws Exception {
        var item = new TransferBatchItemRequest();
        item.setDestinationAccountId(UUID.fromString(userB.getAccountId()));
        item.setAmount(new BigDecimal("25.50"));

        var request = new TransferBatchRequest();
        request.setSourceAccountId(UUID.fromString(userA.getAccountId()));
        request.setItems(List.of(item));

        String idempotencyKey = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/transfers/batch")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Authorization", "Bearer " + userA.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Transfer successful"));

        Account accountA = accountRepository.findById(UUID.fromString(userA.getAccountId())).get();
        Account accountB = accountRepository.findById(UUID.fromString(userB.getAccountId())).get();

        assertThat(accountA.getBalanceCents()).isEqualTo(10000L - 2550L);
        assertThat(accountB.getBalanceCents()).isEqualTo(2550L);
    }

    @Test
    void shouldReturnSameResultForIdempotentRequest() throws Exception {
        var item = new TransferBatchItemRequest();
        item.setDestinationAccountId(UUID.fromString(userB.getAccountId()));
        item.setAmount(new BigDecimal("10.00"));

        var request = new TransferBatchRequest();
        request.setSourceAccountId(UUID.fromString(userA.getAccountId()));
        request.setItems(List.of(item));

        String idempotencyKey = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/transfers/batch")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Authorization", "Bearer " + userA.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(post("/api/v1/transfers/batch")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Authorization", "Bearer " + userA.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Request already processed"));

        Account accountA = accountRepository.findById(UUID.fromString(userA.getAccountId())).get();
        assertThat(accountA.getBalanceCents()).isEqualTo(10000L - 1000L);
    }

    @Test
    void shouldFailTransferDueToInsufficientFunds() throws Exception {
        var item = new TransferBatchItemRequest();
        item.setDestinationAccountId(UUID.fromString(userB.getAccountId()));
        item.setAmount(new BigDecimal("120.00"));

        var request = new TransferBatchRequest();
        request.setSourceAccountId(UUID.fromString(userA.getAccountId()));
        request.setItems(List.of(item));

        String idempotencyKey = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/transfers/batch")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Authorization", "Bearer " + userA.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Insufficient funds"));

        Account accountA = accountRepository.findById(UUID.fromString(userA.getAccountId())).get();
        assertThat(accountA.getBalanceCents()).isEqualTo(10000L);
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