package com.nlb.controller;

import com.nlb.dto.transaction.TransferBatchItemRequest;
import com.nlb.dto.transaction.TransferBatchRequest;
import com.nlb.dto.transaction.TransferBatchResponse;
import com.nlb.interfaces.TransferBatchService;
import com.nlb.service.models.BatchItem;
import com.nlb.service.models.BatchTransferRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Validated // Potrebno da bi @NotBlank na header-u radilo
public class TransferController {

    private final TransferBatchService transferBatchService;

    @PostMapping("/batch")
    public ResponseEntity<TransferBatchResponse> executeBatchTransfer(
            @RequestHeader("Idempotency-Key") @NotBlank(message = "Idempotency-Key header is required") String idempotencyKey,
            @RequestBody @Valid TransferBatchRequest request,
            Authentication authentication // Spring Security će automatski ubaciti ovo
    ) {
        // 1. Izdvojimo ID korisnika iz JWT tokena
        // Postavili smo 'subject' da bude UUID korisnika tokom registracije/logina
        UUID initiatedByUserId = UUID.fromString(authentication.getName());

        // 2. Mapiramo DTO u interni servisni model
        var serviceRequest = new BatchTransferRequest(
                idempotencyKey,
                initiatedByUserId,
                request.getSourceAccountId(),
                mapToServiceItems(request.getItems())
        );

        // 3. Pozivamo servisnu logiku
        var serviceResponse = transferBatchService.executeBatchTransfer(serviceRequest);

        // 4. Mapiramo odgovor servisa nazad u DTO
        var responseDto = new TransferBatchResponse(
                serviceResponse.paymentOrderId().toString(),
                serviceResponse.status().name(), // npr. "COMPLETED"
                serviceResponse.message()
        );

        // 5. Vraćamo HTTP odgovor
        // Ako je status FAILED, možemo vratiti 400 Bad Request,
        // a ako je COMPLETED, vraćamo 200 OK.
        HttpStatus status = switch (serviceResponse.status()) {
            case COMPLETED -> HttpStatus.OK;
            case FAILED -> HttpStatus.BAD_REQUEST; // Poslovna greška (npr. nema sredstava)
            case PENDING -> HttpStatus.ACCEPTED; // Iako ga mi ne koristimo, za budućnost
        };

        return new ResponseEntity<>(responseDto, status);
    }

    // Helper metoda za mapiranje DTO liste u servisnu listu
    private List<BatchItem> mapToServiceItems(List<TransferBatchItemRequest> dtoItems) {
        return dtoItems.stream()
                .map(dto -> new BatchItem(dto.getDestinationAccountId(), dto.getAmount()))
                .collect(Collectors.toList());
    }
}