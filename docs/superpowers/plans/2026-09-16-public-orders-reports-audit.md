# Public Orders, Reports, Receipts and Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Phase 4 backend APIs required before the Vue frontend leaves Supabase.

**Architecture:** Extend the modular monolith with public-order, reporting, receipt projection and audit-query modules. Keep public catalog/order traffic token-scoped and authenticated operations tenant-scoped; compute all money server-side from persisted sale/catalog snapshots.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, PostgreSQL 17, Flyway, JUnit 5, MockMvc, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-16-public-orders-reports-audit-design.md`

## Global Constraints
- `empresa_id` is always derived server-side for authenticated operations.
- Public catalog/order endpoints authenticate only by cryptographically random client token.
- VENDEDOR never receives cost, profit or report data.
- Cancelled sales never contribute to report/dashboard/chart financial totals.
- Existing public order URLs must be preservable during migration.
- Money uses BigDecimal/NUMERIC.
- Existing Phase 1-3 APIs remain backward compatible.

---

### Task 1: Public-order persistence and security
**Files:** V4 migration, Client entity/repository/response/service, SecurityConfig, token support.
- [ ] Write schema/integration tests for V4, token regeneration and public allow-list.
- [ ] Add V4 tables/constraints/indexes and client token column.
- [ ] Add client order-link and hidden-products authenticated APIs.
- [ ] Verify tenant and role restrictions.

### Task 2: Public catalog and order submission
**Files:** `publicorder/*`, product/pricing/photo repositories as needed.
- [ ] Write tests proving hidden products are omitted and client pricing is used.
- [ ] Implement GET catalog/recent and POST public order endpoints.
- [ ] Re-price order lines on server and snapshot names/prices.
- [ ] Verify old/regenerated token behavior and invalid-token 404.

### Task 3: Internal order workflow
**Files:** `customerorder/*`.
- [ ] Write tests for list/view/reject/delete/claim/release/convert and tenant isolation.
- [ ] Implement atomic expiring conversion claim.
- [ ] Validate conversion sale belongs to same company/client and is active.
- [ ] Record audit events for administrative transitions.

### Task 4: Reports, dashboard and charts
**Files:** `report/*`, SaleRepository, timezone configuration.
- [ ] Write tests for cancelled-sale exclusion, totals and seller denial.
- [ ] Implement filtered sales report summary/details.
- [ ] Implement overview and DAY/MONTH/YEAR chart projections.
- [ ] Keep all financial endpoints OWNER/ADMIN only.

### Task 5: Receipt projection and audit queries
**Files:** `receipt/*`, `audit/*`, company/client getters/repositories.
- [ ] Write receipt test proving no cost/profit fields are serialized.
- [ ] Implement receipt projection with company/client contact information.
- [ ] Implement owner company-audit query and platform company-audit query.
- [ ] Add missing audit events for critical Phase 3 mutations.

### Task 6: Full verification and delta package
- [ ] Update schema cleanup fixtures and schema assertions.
- [ ] Run `./mvnw -B verify` when Maven Central is reachable.
- [ ] Run `git diff --check` equivalent/static checks in the workspace.
- [ ] Package only new/modified Phase 4 files preserving relative paths.
