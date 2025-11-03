package com.nlb.controller;

import com.nlb.dto.report.IntegrationFailureReport;
import com.nlb.dto.report.PaymentOrderReport;
import com.nlb.dto.report.TransactionReport;
import com.nlb.interfaces.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/orders")
    public List<PaymentOrderReport> getMyPaymentOrders(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return reportService.getPaymentOrdersForUser(userId).stream()
                .map(PaymentOrderReport::fromEntitySummary)
                .collect(Collectors.toList());
    }

    @GetMapping("/orders/{orderId}")
    public PaymentOrderReport getMyPaymentOrderDetails(
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        var order = reportService.getPaymentOrderDetails(userId, orderId);
        return PaymentOrderReport.fromEntityDetails(order);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public List<TransactionReport> getTransactionsForAccount(
            @PathVariable UUID accountId,
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return reportService.getTransactionsForAccount(userId, accountId).stream()
                .map(TransactionReport::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping("/failures")
    public List<IntegrationFailureReport> getAllFailures(Authentication authentication) {
        // TODO: Dodati proveru za ADMIN rolu u budućnosti
        return reportService.getAllIntegrationFailures().stream()
                .map(IntegrationFailureReport::fromEntity)
                .collect(Collectors.toList());
    }
}
