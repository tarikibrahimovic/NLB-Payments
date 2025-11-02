package com.nlb.dto.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferBatchResponse {
    private String paymentOrderId;
    private String status;
    private String message;
}