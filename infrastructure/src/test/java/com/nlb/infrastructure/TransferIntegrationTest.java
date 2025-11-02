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
import org.springframework.transaction.annotation.Transactional; // <--- IMPORTUJ OVO

@SpringBootTest(classes = InfrastructureApplication.class) // <--- (Ne zaboravi rešenje od malopre)
@AutoConfigureMockMvc // Omogućava nam da simuliramo HTTP pozive (MockMvc)
@Testcontainers // Aktivira Testcontainers
@ActiveProfiles("test") // Opciono, ako imate testni profil
@Transactional
public class TransferIntegrationTest {

    @Autowired
    private MockMvc mvc; // Za slanje HTTP zahteva

    @Autowired
    private ObjectMapper objectMapper; // Za konverziju objekata u JSON

    @Autowired
    private AccountRepository accountRepository; // Za direktnu proveru stanja u bazi

    private RegisterResponse userA;
    private RegisterResponse userB;

    @BeforeEach
    void setUp() throws Exception {
        // Pomoćna metoda za registraciju korisnika
        userA = registerUser("userA@example.com", "User A");
        userB = registerUser("userB@example.com", "User B");

        // "Uplatimo" 100 EUR korisniku A da ima sa čim da radi
        Account accountA = accountRepository.findById(UUID.fromString(userA.getAccountId())).get();
        accountA.setBalanceCents(10000L); // 100.00 EUR
        accountRepository.save(accountA);
    }

    /**
     * Use Case 1: Transfer "Happy Path"
     */
    @Test
    void shouldExecuteBatchTransferSuccessfully() throws Exception {
        var item = new TransferBatchItemRequest();
        item.setDestinationAccountId(UUID.fromString(userB.getAccountId()));
        item.setAmount(new BigDecimal("25.50")); // 25.50 EUR

        var request = new TransferBatchRequest();
        request.setSourceAccountId(UUID.fromString(userA.getAccountId()));
        request.setItems(List.of(item));

        String idempotencyKey = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/transfers/batch")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Authorization", "Bearer " + userA.getToken()) // Koristimo token korisnika A
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Transfer successful"));

        // Proverimo stanje u bazi
        Account accountA = accountRepository.findById(UUID.fromString(userA.getAccountId())).get();
        Account accountB = accountRepository.findById(UUID.fromString(userB.getAccountId())).get();

        assertThat(accountA.getBalanceCents()).isEqualTo(10000L - 2550L); // 74.50
        assertThat(accountB.getBalanceCents()).isEqualTo(2550L); // 25.50
    }

    /**
     * Use Case 2: Idempotency
     */
    @Test
    void shouldReturnSameResultForIdempotentRequest() throws Exception {
        // ... (isti setup kao gore)
        var item = new TransferBatchItemRequest();
        item.setDestinationAccountId(UUID.fromString(userB.getAccountId()));
        item.setAmount(new BigDecimal("10.00"));

        var request = new TransferBatchRequest();
        request.setSourceAccountId(UUID.fromString(userA.getAccountId()));
        request.setItems(List.of(item));

        String idempotencyKey = UUID.randomUUID().toString(); // ISTI ključ

        // 1. Prvi poziv (izvršava se)
        mvc.perform(post("/api/v1/transfers/batch")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Authorization", "Bearer " + userA.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 2. Drugi poziv (vraća snimljen rezultat)
        mvc.perform(post("/api/v1/transfers/batch")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("Authorization", "Bearer " + userA.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Request already processed"));

        // Proverimo stanje - novac je skinut SAMO JEDNOM
        Account accountA = accountRepository.findById(UUID.fromString(userA.getAccountId())).get();
        assertThat(accountA.getBalanceCents()).isEqualTo(10000L - 1000L); // 90.00
    }

    /**
     * Use Case 3: Neuspeh (Insufficient Funds)
     */
    @Test
    void shouldFailTransferDueToInsufficientFunds() throws Exception {
        // Pokušavamo da pošaljemo 120 EUR, a imamo 100 EUR
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
                .andExpect(status().isBadRequest()) // Vraćamo 400
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Insufficient funds"));

        // Proverimo stanje - NIŠTA se nije promenilo
        Account accountA = accountRepository.findById(UUID.fromString(userA.getAccountId())).get();
        assertThat(accountA.getBalanceCents()).isEqualTo(10000L); // Ostalo 100.00
    }

    // --- Pomoćna (helper) metoda ---

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