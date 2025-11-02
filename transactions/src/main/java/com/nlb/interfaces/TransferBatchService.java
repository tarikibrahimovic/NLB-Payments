package com.nlb.interfaces;

import com.nlb.service.models.BatchTransferRequest;
import com.nlb.service.models.BatchTransferResponse;

public interface TransferBatchService {

    /**
     * Izvršava kompletan batch transfer novca.
     * Metoda je idempotentna na osnovu 'request.idempotencyKey'.
     *
     * Ova metoda je odgovorna za:
     * 1. Proveru idempotentnosti.
     * 2. Zaključavanje svih uključenih naloga (source + destinations).
     * 3. Validaciju (vlasništvo, saldo, status naloga).
     * 4. Izvršenje transfera (debit/credit).
     * 5. Zapisivanje PaymentOrder, PaymentOrderItems i Transaction zapisa.
     * 6. Rukovanje greškama (poslovnim i sistemskim).
     *
     * @param request Objekat koji sadrži sve podatke za transfer.
     * @return Rezultat operacije.
     */
    BatchTransferResponse executeBatchTransfer(BatchTransferRequest request);
}