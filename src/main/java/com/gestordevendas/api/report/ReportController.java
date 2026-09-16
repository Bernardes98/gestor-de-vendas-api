package com.gestordevendas.api.report;

import com.gestordevendas.api.sale.SalePaymentType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @GetMapping("/api/reports/sales")
    public SalesReportResponse sales(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID clientId,
        @RequestParam(required = false) SalePaymentType paymentType
    ) { return service.sales(from, to, clientId, paymentType); }

    @GetMapping("/api/dashboard/overview")
    public DashboardOverviewResponse overview() { return service.overview(); }

    @GetMapping("/api/reports/charts")
    public ChartResponse charts(@RequestParam(defaultValue = "DAY") ChartPeriod period,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.charts(period, date);
    }
}
