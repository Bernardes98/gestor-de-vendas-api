package com.gestordevendas.api.receipt;

import com.gestordevendas.api.sale.SalePaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleReceiptResponse(
    CompanyInfo company,
    ClientInfo client,
    long number,
    Instant soldAt,
    SalePaymentType paymentType,
    BigDecimal total,
    BigDecimal paidAmount,
    BigDecimal outstanding,
    List<Item> items
) {
    public record CompanyInfo(String name, String legalName, String document, String phone, String email,
                              String address, String city, String logoUrl) {}
    public record ClientInfo(String name, String document, String phone, String address, String city) {}
    public record Item(String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}
}
