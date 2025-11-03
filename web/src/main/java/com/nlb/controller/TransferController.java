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
@Validated
public class TransferController {

    private final TransferBatchService transferBatchService;

    @PostMapping("/batch")
    public ResponseEntity<TransferBatchResponse> executeBatchTransfer(
            @RequestHeader("Idempotency-Key") @NotBlank(message = "Idempotency-Key header is required") String idempotencyKey,
            @RequestBody @Valid TransferBatchRequest request,
            Authentication authentication
    ) {
        UUID initiatedByUserId = UUID.fromString(authentication.getName());

        var serviceRequest = new BatchTransferRequest(
                idempotencyKey,
                initiatedByUserId,
                request.getSourceAccountId(),
                mapToServiceItems(request.getItems())
        );

        var serviceResponse = transferBatchService.executeBatchTransfer(serviceRequest);

        var responseDto = new TransferBatchResponse(
                serviceResponse.paymentOrderId().toString(),
                serviceResponse.status().name(),
                serviceResponse.message()
        );

        HttpStatus status = switch (serviceResponse.status()) {
            case COMPLETED -> HttpStatus.OK;
            case FAILED -> HttpStatus.BAD_REQUEST;
            case PENDING -> HttpStatus.ACCEPTED;
        };

        return new ResponseEntity<>(responseDto, status);
    }

    private List<BatchItem> mapToServiceItems(List<TransferBatchItemRequest> dtoItems) {
        return dtoItems.stream()
                .map(dto -> new BatchItem(dto.getDestinationAccountId(), dto.getAmount()))
                .collect(Collectors.toList());
    }
}