# Public Orders, Reports, Receipts and Audit Design

## Goal
Close the backend feature surface required before migrating the Vue frontend away from Supabase: public customer-order links, internal order workflow, reporting/dashboard/chart queries, sale receipt data for print/WhatsApp, and tenant-scoped audit consultation.

## Public order links
Each client can have one cryptographically-random public order token. The raw token is stored because the authenticated UI must be able to show/copy the existing link and migration must preserve legacy URLs. Regeneration replaces the token and invalidates the old URL immediately. Only OWNER/ADMIN can regenerate links or configure hidden products.

A client may hide selected products from its public catalog. The catalog returns only active products not hidden for that client, client-specific prices when present, otherwise the product sale price, product photos, and company branding. Public endpoints never expose cost, stock, internal IDs outside product/client/order identifiers needed by the flow, or audit information.

Public order creation re-prices all lines on the server from the current client condition. The browser sends only product id and quantity. Product name, unit price and line total are snapshotted into order items. Empty orders, inactive products, cross-tenant products, and hidden products are rejected.

## Internal order workflow
Statuses are PENDENTE, CONVERTIDO and RECUSADO. Orders have viewed timestamp and a short-lived conversion claim. Claiming is atomic: an unclaimed/expired order can be claimed by one user; the same user can re-enter; another user gets `false` until the claim expires or is released. Conversion links an active sale from the same tenant and client. Rejection clears any conversion claim. Only rejected orders can be physically deleted, and only OWNER/ADMIN may delete them. VENDEDOR can list, view, claim/release, reject and mark converted because these are operational sales actions.

## Reporting
All financial report endpoints are OWNER/ADMIN only. Cancelled sales are excluded. Filters support date range, client and payment type. Responses include summary (count, revenue, cost, profit, margin and markup) plus sale rows. Dashboard returns today/month/year summaries, latest active sales, items sold today, low-stock products and top clients for the month. Charts return DAY/MONTH/YEAR points and previous-period comparisons using company/business timezone supplied as application configuration, defaulting to America/Sao_Paulo.

## Receipt/WhatsApp projection
`GET /api/sales/{id}/receipt` is available to OWNER/ADMIN/VENDEDOR and returns only customer-facing data: company display/legal name, document, phone/address/city and logo URL; client name/phone/address/city; sale number/date/payment state; line quantity/unit price/total; total/paid/outstanding. It never includes cost or profit.

## Audit consultation and events
OWNER can query its own company's audit log with action/entity/date filters and limit. PLATFORM_ADMIN can query a company's audit through a platform endpoint. Metadata remains sanitized by `AuditService`. Phase 4 records public-order-link regeneration, hidden-product changes, order rejection/conversion/deletion, and critical sale/purchase/stock/receivable administrative mutations not already covered.

## Persistence
Flyway V4 adds: client public token, hidden-product relation, customer orders and order items. Tenant-safe composite foreign keys prevent cross-company linkage. Order snapshots preserve history even after catalog changes.

## Security
Public endpoints are explicitly allow-listed in Spring Security. All internal endpoints derive company from authenticated membership. No request accepts arbitrary `empresa_id`. Public tokens are at least 256 bits of randomness. Financial reports are forbidden to VENDEDOR. Receipt responses omit sensitive financial fields.

## Tests
Integration tests cover token regeneration/invalidation, hidden products and client pricing, public server-side pricing, order workflow/claim isolation, cross-tenant denial, seller operational permissions, report exclusion of cancelled sales, seller report denial, receipt data without cost, and audit tenant isolation. Existing tests must remain green.
