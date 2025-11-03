package com.nlb.interfaces;

import com.nlb.service.models.BatchTransferRequest;
import com.nlb.service.models.BatchTransferResponse;

public interface TransferBatchService {
    BatchTransferResponse executeBatchTransfer(BatchTransferRequest request);
}