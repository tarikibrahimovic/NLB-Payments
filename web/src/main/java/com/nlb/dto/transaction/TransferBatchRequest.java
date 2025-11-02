package com.nlb.dto.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class TransferBatchRequest {

    @NotNull(message = "Source account ID cannot be null")
    private UUID sourceAccountId;

    @Valid // validira i objekte unutar liste
    @NotEmpty(message = "Items list cannot be empty")
    private List<TransferBatchItemRequest> items;
}