package com.gestordevendas.api.report;

import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.product.*;
import com.gestordevendas.api.sale.*;
import com.gestordevendas.api.storage.ObjectStorage;
import com.gestordevendas.api.tenant.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private final SaleRepository saleRepository;
    private final SaleItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final ProductPhotoRepository photoRepository;
    private final ObjectStorage objectStorage;
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final ZoneId zone;

    public ReportService(SaleRepository saleRepository, SaleItemRepository itemRepository,
                         ProductRepository productRepository, ProductPhotoRepository photoRepository,
                         ObjectStorage objectStorage, CurrentUserService currentUserService,
                         TenantContextService tenantContextService, TenantGuard tenantGuard,
                         BusinessTimeProperties timeProperties) {
        this.saleRepository = saleRepository; this.itemRepository = itemRepository; this.productRepository = productRepository;
        this.photoRepository = photoRepository; this.objectStorage = objectStorage; this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService; this.tenantGuard = tenantGuard;
        this.zone = ZoneId.of(timeProperties.effectiveZoneId());
    }

    @Transactional(readOnly = true)
    public SalesReportResponse sales(LocalDate from, LocalDate to, UUID clientId, SalePaymentType paymentType) {
        TenantContext context = financeContext();
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) throw new com.gestordevendas.api.common.error.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
            "INVALID_PERIOD", "Data inicial não pode ser posterior à data final.");
        List<Sale> values = activeSales(context.companyId(), start, end).stream()
            .filter(sale -> clientId == null || (sale.getClient() != null && clientId.equals(sale.getClient().getId())))
            .filter(sale -> paymentType == null || sale.getPaymentType() == paymentType)
            .toList();
        BigDecimal revenue = sum(values, Sale::getTotal); BigDecimal cost = sum(values, Sale::getCostTotal); BigDecimal profit = sum(values, Sale::getProfitTotal);
        SalesReportResponse.Summary summary = new SalesReportResponse.Summary(values.size(), revenue, cost, profit,
            percent(profit, revenue), percent(profit, cost));
        List<SalesReportResponse.Row> rows = values.stream().map(sale -> new SalesReportResponse.Row(sale.getId(), sale.getNumber(), sale.getSoldAt(),
            sale.getClient() == null ? null : sale.getClient().getId(), sale.getClient() == null ? "Venda rápida" : sale.getClient().getName(),
            sale.getPaymentType(), money(sale.getTotal()), money(sale.getCostTotal()), money(sale.getProfitTotal()), percent(sale.getProfitTotal(), sale.getTotal()))).toList();
        return new SalesReportResponse(summary, rows);
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview() {
        TenantContext context = financeContext(); LocalDate today = LocalDate.now(zone);
        List<Sale> todaySales = activeSales(context.companyId(), today, today);
        List<Sale> monthSales = activeSales(context.companyId(), today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
        List<Sale> yearSales = activeSales(context.companyId(), LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31));

        Map<UUID, DashboardOverviewResponse.ItemSold> sold = new HashMap<>();
        for (Sale sale : todaySales) {
            for (SaleItem item : itemRepository.findAllByCompanyIdAndSaleId(context.companyId(), sale.getId())) {
                UUID productId = item.getProduct().getId();
                DashboardOverviewResponse.ItemSold previous = sold.get(productId);
                BigDecimal quantity = item.getQuantity().add(previous == null ? BigDecimal.ZERO : previous.quantity());
                sold.put(productId, new DashboardOverviewResponse.ItemSold(productId, item.getProductName(), quantity,
                    previous == null ? firstPhoto(context.companyId(), productId) : previous.photoUrl()));
            }
        }

        List<DashboardOverviewResponse.LowStockProduct> lowStock = productRepository.findAllByCompanyIdAndActiveTrueOrderByNameAsc(context.companyId()).stream()
            .filter(Product::isStockControlled)
            .filter(product -> product.getCurrentStock().compareTo(product.getMinimumStock()) <= 0)
            .map(product -> new DashboardOverviewResponse.LowStockProduct(product.getId(), product.getName(), product.getCurrentStock(), product.getMinimumStock()))
            .toList();

        Map<UUID, BigDecimal> topTotals = new HashMap<>(); Map<UUID, String> topNames = new HashMap<>();
        for (Sale sale : monthSales) if (sale.getClient() != null) {
            topTotals.merge(sale.getClient().getId(), sale.getTotal(), BigDecimal::add); topNames.put(sale.getClient().getId(), sale.getClient().getName());
        }
        List<DashboardOverviewResponse.TopClient> topClients = topTotals.entrySet().stream()
            .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed()).limit(5)
            .map(entry -> new DashboardOverviewResponse.TopClient(entry.getKey(), topNames.get(entry.getKey()), money(entry.getValue()))).toList();

        List<DashboardOverviewResponse.LatestSale> latest = saleRepository.findTop5Active(context.companyId()).stream()
            .map(sale -> new DashboardOverviewResponse.LatestSale(sale.getId(), sale.getNumber(), sale.getClient() == null ? "Venda rápida" : sale.getClient().getName(),
                sale.getSoldAt(), money(sale.getTotal()), money(sale.getProfitTotal()))).toList();

        return new DashboardOverviewResponse(summary(todaySales), summary(monthSales), summary(yearSales), latest,
            sold.values().stream().sorted(Comparator.comparing(DashboardOverviewResponse.ItemSold::quantity).reversed()).toList(), lowStock, topClients);
    }

    @Transactional(readOnly = true)
    public ChartResponse charts(ChartPeriod period, LocalDate date) {
        TenantContext context = financeContext(); LocalDate anchor = date == null ? LocalDate.now(zone) : date;
        Range current = currentRange(period, anchor); Range previous = previousRange(period, anchor);
        List<Sale> currentSales = activeSales(context.companyId(), current.from(), current.to());
        List<Sale> previousSales = activeSales(context.companyId(), previous.from(), previous.to());
        BigDecimal currentRevenue = sum(currentSales, Sale::getTotal); BigDecimal currentProfit = sum(currentSales, Sale::getProfitTotal);
        BigDecimal previousRevenue = sum(previousSales, Sale::getTotal); BigDecimal previousProfit = sum(previousSales, Sale::getProfitTotal);
        return new ChartResponse(period, currentRevenue, currentProfit, previousRevenue, previousProfit,
            growth(currentRevenue, previousRevenue), growth(currentProfit, previousProfit), points(period, anchor, currentSales));
    }

    private List<ChartResponse.Point> points(ChartPeriod period, LocalDate anchor, List<Sale> sales) {
        if (period == ChartPeriod.DAY) {
            return java.util.stream.IntStream.range(0, 24).mapToObj(hour -> {
                BigDecimal revenue = BigDecimal.ZERO, profit = BigDecimal.ZERO;
                for (Sale sale : sales) if (sale.getSoldAt().atZone(zone).getHour() == hour) { revenue = revenue.add(sale.getTotal()); profit = profit.add(sale.getProfitTotal()); }
                String h = String.format("%02d", hour);
                return new ChartResponse.Point(anchor + "-" + h, h + ":00", h + "h", money(revenue), money(profit));
            }).toList();
        }
        if (period == ChartPeriod.MONTH) {
            int days = anchor.lengthOfMonth();
            return java.util.stream.IntStream.rangeClosed(1, days).mapToObj(day -> {
                LocalDate target = anchor.withDayOfMonth(day); BigDecimal revenue = BigDecimal.ZERO, profit = BigDecimal.ZERO;
                for (Sale sale : sales) if (sale.getSoldAt().atZone(zone).toLocalDate().equals(target)) { revenue = revenue.add(sale.getTotal()); profit = profit.add(sale.getProfitTotal()); }
                return new ChartResponse.Point(target.toString(), String.format("%02d/%02d", day, anchor.getMonthValue()), String.valueOf(day), money(revenue), money(profit));
            }).toList();
        }
        return java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(month -> {
            BigDecimal revenue = BigDecimal.ZERO, profit = BigDecimal.ZERO;
            for (Sale sale : sales) if (sale.getSoldAt().atZone(zone).getMonthValue() == month) { revenue = revenue.add(sale.getTotal()); profit = profit.add(sale.getProfitTotal()); }
            YearMonth ym = YearMonth.of(anchor.getYear(), month); String shortLabel = ym.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, new Locale("pt", "BR"));
            return new ChartResponse.Point(ym.toString(), shortLabel + " " + anchor.getYear(), capitalize(shortLabel), money(revenue), money(profit));
        }).toList();
    }

    private String capitalize(String value) { return value == null || value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1).replace(".", ""); }
    private DashboardOverviewResponse.PeriodSummary summary(List<Sale> sales) {
        BigDecimal revenue = sum(sales, Sale::getTotal), cost = sum(sales, Sale::getCostTotal), profit = sum(sales, Sale::getProfitTotal);
        return new DashboardOverviewResponse.PeriodSummary(sales.size(), revenue, cost, profit, percent(profit, revenue));
    }
    private List<Sale> activeSales(UUID companyId, LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(zone).toInstant(); Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        return saleRepository.findAllActiveInRange(companyId, start, end);
    }
    private String firstPhoto(UUID companyId, UUID productId) {
        return photoRepository.findFirstByCompanyIdAndProductIdOrderByOrderIndexAsc(companyId, productId)
            .map(photo -> photo.getUrl()).orElse(null);
    }
    private Range currentRange(ChartPeriod period, LocalDate date) {
        return switch (period) {
            case DAY -> new Range(date, date);
            case MONTH -> new Range(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
            case YEAR -> new Range(LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
        };
    }
    private Range previousRange(ChartPeriod period, LocalDate date) {
        return switch (period) {
            case DAY -> new Range(date.minusDays(1), date.minusDays(1));
            case MONTH -> { LocalDate p = date.minusMonths(1); yield new Range(p.withDayOfMonth(1), p.withDayOfMonth(p.lengthOfMonth())); }
            case YEAR -> new Range(LocalDate.of(date.getYear() - 1, 1, 1), LocalDate.of(date.getYear() - 1, 12, 31));
        };
    }
    private BigDecimal growth(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) return current.signum() == 0 ? BigDecimal.ZERO.setScale(2) : null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }
    private BigDecimal sum(List<Sale> sales, java.util.function.Function<Sale, BigDecimal> getter) {
        return money(sales.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }
    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private TenantContext financeContext() { TenantContext context = tenantContextService.requireForUser(currentUserService.requireUserId()); tenantGuard.requireOwnerOrAdmin(context); return context; }
    private record Range(LocalDate from, LocalDate to) {}
}
