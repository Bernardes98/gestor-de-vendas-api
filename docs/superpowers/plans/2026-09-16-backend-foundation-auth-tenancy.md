# Backend Foundation, Auth and Tenancy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar o novo `gestor-de-vendas-api` com PostgreSQL próprio, autenticação segura, multiempresa, convites e regras OWNER/ADMIN/VENDEDOR, sem ainda migrar os módulos de negócio do Vue.

**Architecture:** Um monólito modular Spring Boot expõe API REST e persiste em PostgreSQL via JPA/Flyway. O JWT identifica o usuário; a empresa, o status e o papel são resolvidos no servidor a partir de `empresa_usuarios` em toda operação protegida, permitindo bloqueio imediato sem confiar em `empresa_id` vindo do frontend. Refresh tokens, convites e redefinição usam tokens aleatórios cuja versão bruta nunca é persistida.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Maven, Spring Web MVC, Spring Security, OAuth2 JOSE/Nimbus para JWT, Spring Data JPA, PostgreSQL, Flyway, Bean Validation, Actuator, Testcontainers, JUnit 5, Mockito, RestClient para Brevo, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-16-backend-proprio-migracao-supabase-design.md`

## Global Constraints

- Java 21.
- Spring Boot 3; este plano fixa 3.5.16, a versão 3.x estável mais recente no momento da elaboração.
- PostgreSQL único e multiempresa.
- Todos os IDs principais são UUID.
- Valores monetários futuros usarão PostgreSQL `NUMERIC` e Java `BigDecimal`.
- Papéis de empresa fixos: `OWNER`, `ADMIN`, `VENDEDOR`.
- `PLATFORM_ADMIN` é autorização global separada.
- ADMIN pode convidar/criar usuário, mas o convidado nasce obrigatoriamente `VENDEDOR`.
- Apenas OWNER altera papel e remove vínculo de usuário.
- OWNER e ADMIN podem bloquear/desbloquear usuários.
- Empresa `BLOQUEADA` impede operação sem apagar dados.
- Usuário `BLOQUEADO` impede operação sem apagar dados.
- Access token: 15 minutos.
- Refresh token: 30 dias, cookie `HttpOnly`, `Secure` em produção e `SameSite=Lax` quando app/API estiverem no mesmo site.
- Redefinição de senha: token de uso único com 30 minutos.
- Senhas: BCrypt via Spring Security; nunca registrar senha em logs.
- Tokens brutos de refresh/reset/convite nunca são persistidos nem logados.
- Frontend nunca é fonte confiável de `empresa_id`.
- Toda resposta de erro usa `code`, `message` e `requestId`; sem stack trace/SQL/segredos.
- TDD obrigatório: teste falhando -> implementação mínima -> teste passando -> commit.

---

## File Structure

O novo repositório será irmão do frontend atual:

```text
gestor-de-vendas-api/
├── pom.xml
├── Dockerfile
├── compose.yaml
├── .env.example
├── .gitignore
├── README.md
├── src/main/java/com/gestordevendas/api/
│   ├── GestorDeVendasApiApplication.java
│   ├── common/
│   │   ├── error/ApiError.java
│   │   ├── error/ApiException.java
│   │   ├── error/GlobalExceptionHandler.java
│   │   ├── request/RequestIdFilter.java
│   │   ├── security/SecurityConfig.java
│   │   ├── security/CurrentUser.java
│   │   ├── security/CurrentUserService.java
│   │   └── token/SecureTokenService.java
│   ├── company/
│   │   ├── Company.java
│   │   ├── CompanyRepository.java
│   │   └── CompanyStatus.java
│   ├── user/
│   │   ├── User.java
│   │   ├── UserRepository.java
│   │   ├── CompanyMembership.java
│   │   ├── CompanyMembershipRepository.java
│   │   ├── CompanyRole.java
│   │   ├── UserManagementController.java
│   │   └── UserManagementService.java
│   ├── auth/
│   │   ├── AuthController.java
│   │   ├── AuthService.java
│   │   ├── JwtService.java
│   │   ├── RefreshToken.java
│   │   ├── RefreshTokenRepository.java
│   │   ├── PasswordResetToken.java
│   │   ├── PasswordResetTokenRepository.java
│   │   ├── AuthRateLimiter.java
│   │   └── dto/... 
│   ├── invite/
│   │   ├── CompanyInvite.java
│   │   ├── UserInvite.java
│   │   ├── CompanyInviteRepository.java
│   │   ├── UserInviteRepository.java
│   │   ├── InviteController.java
│   │   └── InviteService.java
│   ├── platform/
│   │   ├── PlatformController.java
│   │   └── PlatformService.java
│   ├── mail/
│   │   ├── EmailSender.java
│   │   ├── BrevoEmailSender.java
│   │   └── MailProperties.java
│   ├── audit/
│   │   ├── AuditEvent.java
│   │   ├── AuditRepository.java
│   │   └── AuditService.java
│   └── tenant/
│       ├── TenantContext.java
│       ├── TenantContextService.java
│       └── TenantGuard.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   └── db/migration/
│       └── V1__identity_and_tenancy.sql
└── src/test/java/com/gestordevendas/api/
    ├── support/PostgresIntegrationTest.java
    ├── auth/AuthFlowIntegrationTest.java
    ├── auth/PasswordResetIntegrationTest.java
    ├── platform/PlatformCompanyIntegrationTest.java
    ├── user/UserPermissionsIntegrationTest.java
    └── tenant/TenantIsolationIntegrationTest.java
```

### Boundary decisions

- `auth` conhece usuários e tokens, mas não implementa regras de clientes/produtos/vendas.
- `tenant` resolve vínculo/status/papel a partir do usuário autenticado e é reutilizado pelos módulos futuros.
- `platform` gerencia empresas e convite inicial, sem ganhar permissão implícita sobre dados de negócio.
- `user` gerencia vínculos internos e regras OWNER/ADMIN/VENDEDOR.
- `mail` abstrai Brevo para testes não dependerem da internet.
- `audit` oferece uma única porta para registrar eventos sensíveis.

---

### Task 1: Bootstrap do repositório e API base

**Files:**
- Create: `../gestor-de-vendas-api/pom.xml`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/GestorDeVendasApiApplication.java`
- Create: `../gestor-de-vendas-api/src/main/resources/application.yml`
- Create: `../gestor-de-vendas-api/src/main/resources/application-local.yml`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/common/error/ApiError.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/common/error/ApiException.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/common/error/GlobalExceptionHandler.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/common/request/RequestIdFilter.java`
- Create: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/ApiSmokeTest.java`

**Interfaces:**
- Produces: `ApiError(String code, String message, String requestId)`.
- Produces: `ApiException(HttpStatus status, String code, String message)`.
- Produces: request header/response header `X-Request-Id` and MDC key `requestId`.

- [ ] **Step 1: Criar o repositório separado e o `pom.xml`**

```bash
cd ..
mkdir gestor-de-vendas-api
cd gestor-de-vendas-api
git init
```

Use este núcleo no `pom.xml`:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.16</version>
  <relativePath/>
</parent>
<groupId>com.gestordevendas</groupId>
<artifactId>gestor-de-vendas-api</artifactId>
<version>0.0.1-SNAPSHOT</version>
<properties>
  <java.version>21</java.version>
</properties>
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
</dependencies>
```

- [ ] **Step 2: Escrever primeiro o teste de contexto e health**

```java
@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {
    @Autowired MockMvc mvc;

    @Test
    void contextLoadsAndHealthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

- [ ] **Step 3: Rodar para confirmar a falha antes da aplicação/configuração existir**

Run: `./mvnw test -Dtest=ApiSmokeTest` ou `mvn test -Dtest=ApiSmokeTest`  
Expected: FAIL por classe principal/configuração ausente.

- [ ] **Step 4: Criar aplicação e configuração mínima**

```java
@SpringBootApplication
public class GestorDeVendasApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(GestorDeVendasApiApplication.class, args);
    }
}
```

```yaml
spring:
  application:
    name: gestor-de-vendas-api
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never
```

- [ ] **Step 5: Implementar erro padronizado e request ID**

```java
public record ApiError(String code, String message, String requestId) {}
```

```java
public final class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
```

`RequestIdFilter` deve aceitar `X-Request-Id` válido já fornecido ou gerar UUID novo, colocar no MDC e devolver o mesmo header.

- [ ] **Step 6: Testar formato de erro com endpoint de teste interno no próprio teste**

Use `@TestConfiguration` com controller que lança `new ApiException(BAD_REQUEST, "TEST_ERROR", "Falha controlada")` e espere:

```json
{"code":"TEST_ERROR","message":"Falha controlada","requestId":"<não vazio>"}
```

- [ ] **Step 7: Rodar todos os testes e commit**

Run: `mvn test`  
Expected: PASS.

```bash
git add .
git commit -m "chore: bootstrap spring boot api"
```

---

### Task 2: PostgreSQL local, Testcontainers e schema de identidade

**Files:**
- Create: `../gestor-de-vendas-api/compose.yaml`
- Create: `../gestor-de-vendas-api/.env.example`
- Create: `../gestor-de-vendas-api/.gitignore`
- Create: `../gestor-de-vendas-api/src/main/resources/db/migration/V1__identity_and_tenancy.sql`
- Create: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/support/PostgresIntegrationTest.java`
- Create: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/support/SchemaIntegrationTest.java`

**Interfaces:**
- Produces tables: `empresas`, `usuarios`, `empresa_usuarios`, `refresh_tokens`, `password_reset_tokens`, `convites_empresa`, `convites_usuario`, `auditoria`.
- Produces database invariant: one active company context per V1 user through `UNIQUE(usuario_id)` in `empresa_usuarios`; `PLATFORM_ADMIN` may have no company membership.

- [ ] **Step 1: Criar Testcontainers base e teste que consulta tabelas**

```java
@Testcontainers
@SpringBootTest
public abstract class PostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
        .withDatabaseName("gestor_test")
        .withUsername("gestor")
        .withPassword("gestor");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

O teste deve usar `JdbcTemplate` e esperar `to_regclass('public.empresas') = 'empresas'`.

- [ ] **Step 2: Rodar o teste antes da migration**

Run: `mvn test -Dtest=SchemaIntegrationTest`  
Expected: FAIL porque as tabelas ainda não existem.

- [ ] **Step 3: Criar `V1__identity_and_tenancy.sql`**

A migration deve criar, no mínimo, as seguintes colunas:

```sql
CREATE TABLE empresas (
  id uuid PRIMARY KEY,
  slug varchar(120) NOT NULL UNIQUE,
  nome_fantasia varchar(180) NOT NULL,
  razao_social varchar(180),
  cpf_cnpj varchar(20),
  telefone varchar(30),
  email varchar(254),
  endereco varchar(255),
  cidade varchar(120),
  logo_key varchar(500),
  cor_primaria varchar(20) NOT NULL DEFAULT '#f59e0b',
  cor_secundaria varchar(20) NOT NULL DEFAULT '#101827',
  ativa boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE usuarios (
  id uuid PRIMARY KEY,
  email varchar(254) NOT NULL,
  password_hash varchar(100),
  nome varchar(180),
  ativo boolean NOT NULL DEFAULT true,
  platform_admin boolean NOT NULL DEFAULT false,
  must_reset_password boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_usuarios_email_ci ON usuarios (lower(email));

CREATE TABLE empresa_usuarios (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES empresas(id),
  usuario_id uuid NOT NULL REFERENCES usuarios(id),
  perfil varchar(20) NOT NULL CHECK (perfil IN ('OWNER','ADMIN','VENDEDOR')),
  ativo boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_empresa_usuario UNIQUE (empresa_id, usuario_id),
  CONSTRAINT uq_usuario_contexto_v1 UNIQUE (usuario_id)
);
```

Continuar a mesma migration com definições explícitas:

```sql
CREATE TABLE refresh_tokens (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuarios(id),
  token_hash char(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  replaced_by_id uuid REFERENCES refresh_tokens(id),
  user_agent varchar(500),
  ip_address varchar(64),
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz
);
CREATE INDEX idx_refresh_tokens_usuario_active
  ON refresh_tokens(usuario_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE password_reset_tokens (
  id uuid PRIMARY KEY,
  usuario_id uuid NOT NULL REFERENCES usuarios(id),
  token_hash char(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE convites_empresa (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES empresas(id),
  owner_email varchar(254) NOT NULL,
  token_hash char(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  cancelled_at timestamptz,
  created_by uuid NOT NULL REFERENCES usuarios(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE convites_usuario (
  id uuid PRIMARY KEY,
  empresa_id uuid NOT NULL REFERENCES empresas(id),
  email varchar(254) NOT NULL,
  perfil varchar(20) NOT NULL CHECK (perfil IN ('ADMIN','VENDEDOR')),
  token_hash char(64) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  cancelled_at timestamptz,
  created_by uuid NOT NULL REFERENCES usuarios(id),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE auditoria (
  id uuid PRIMARY KEY,
  empresa_id uuid REFERENCES empresas(id),
  usuario_id uuid REFERENCES usuarios(id),
  acao varchar(80) NOT NULL,
  entidade_tipo varchar(80),
  entidade_id uuid,
  motivo varchar(500),
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_convites_empresa_pending
  ON convites_empresa(empresa_id, expires_at) WHERE used_at IS NULL AND cancelled_at IS NULL;
CREATE INDEX idx_convites_usuario_pending
  ON convites_usuario(empresa_id, expires_at) WHERE used_at IS NULL AND cancelled_at IS NULL;
CREATE INDEX idx_auditoria_empresa_created
  ON auditoria(empresa_id, created_at DESC);
```

- [ ] **Step 4: Criar `compose.yaml` local**

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: gestor
      POSTGRES_USER: gestor
      POSTGRES_PASSWORD: gestor_local
    ports:
      - "5432:5432"
    volumes:
      - gestor_postgres:/var/lib/postgresql/data
volumes:
  gestor_postgres:
```

- [ ] **Step 5: Configurar `application-local.yml` exclusivamente por env vars**

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/gestor}
    username: ${DB_USER:gestor}
    password: ${DB_PASSWORD:gestor_local}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
```

- [ ] **Step 6: Rodar teste de schema e commit**

Run: `mvn test -Dtest=SchemaIntegrationTest`  
Expected: PASS e Flyway version `1` aplicada.

```bash
git add .
git commit -m "feat: add identity and tenancy schema"
```

---

### Task 3: Entidades, repositórios e resolução de tenant

**Files:**
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/company/Company.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/company/CompanyRepository.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/user/User.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/user/UserRepository.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/user/CompanyMembership.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/user/CompanyMembershipRepository.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/user/CompanyRole.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/tenant/TenantContext.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/tenant/TenantContextService.java`
- Test: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/tenant/TenantContextServiceTest.java`

**Interfaces:**
- Produces: `TenantContext(UUID companyId, UUID userId, CompanyRole role)`.
- Produces: `TenantContextService.requireForUser(UUID userId)`.
- Errors: `USER_BLOCKED`, `COMPANY_BLOCKED`, `NO_COMPANY_MEMBERSHIP`.

- [ ] **Step 1: Escrever testes de resolução de contexto**

```java
@Test
void blocksUserWhenMembershipIsInactive() {
    when(membershipRepository.findByUserId(userId)).thenReturn(Optional.of(inactiveMembership));
    ApiException ex = assertThrows(ApiException.class, () -> service.requireForUser(userId));
    assertEquals("USER_BLOCKED", ex.code());
}

@Test
void blocksCompanyWhenCompanyIsInactive() {
    when(membershipRepository.findByUserId(userId)).thenReturn(Optional.of(activeMembershipInBlockedCompany));
    ApiException ex = assertThrows(ApiException.class, () -> service.requireForUser(userId));
    assertEquals("COMPANY_BLOCKED", ex.code());
}
```

- [ ] **Step 2: Rodar e confirmar falha**

Run: `mvn test -Dtest=TenantContextServiceTest`  
Expected: FAIL por classes ausentes.

- [ ] **Step 3: Implementar entidades/repositórios mínimos**

`CompanyRole`:

```java
public enum CompanyRole { OWNER, ADMIN, VENDEDOR }
```

`CompanyMembershipRepository` deve expor:

```java
Optional<CompanyMembership> findByUserId(UUID userId);
boolean existsByCompanyIdAndUserId(UUID companyId, UUID userId);
```

- [ ] **Step 4: Implementar `TenantContextService.requireForUser`**

Ordem obrigatória: usuário existe e ativo -> vínculo existe e ativo -> empresa ativa -> retorna contexto. Não usar `empresa_id` de request.

- [ ] **Step 5: Rodar testes e commit**

Run: `mvn test -Dtest=TenantContextServiceTest`  
Expected: PASS.

```bash
git add .
git commit -m "feat: resolve tenant context server side"
```

---

### Task 4: JWT, Spring Security e identidade autenticada

**Files:**
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/common/security/SecurityConfig.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/common/security/CurrentUser.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/common/security/CurrentUserService.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/JwtService.java`
- Create: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/auth/JwtServiceTest.java`

**Interfaces:**
- Produces: `JwtService.issueAccessToken(User user)` -> JWT 15 min.
- Produces: `CurrentUserService.requireUserId()` -> UUID from authenticated `sub`.
- JWT claims allowed: `sub`, `email`, `platform_admin`, `iat`, `exp`, `iss`; tenant/role are deliberately not trusted from token.

- [ ] **Step 1: Escrever teste de expiração/claims do JWT**

```java
@Test
void tokenContainsIdentityButNotTenantAuthorization() {
    String token = jwtService.issueAccessToken(user);
    Jwt jwt = decoder.decode(token);
    assertEquals(user.getId().toString(), jwt.getSubject());
    assertEquals(user.getEmail(), jwt.getClaimAsString("email"));
    assertNull(jwt.getClaim("company_id"));
    assertNull(jwt.getClaim("role"));
    assertTrue(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toMinutes() <= 15);
}
```

- [ ] **Step 2: Rodar e confirmar falha**

Run: `mvn test -Dtest=JwtServiceTest`  
Expected: FAIL.

- [ ] **Step 3: Implementar encoder/decoder Nimbus com segredo Base64 externo**

Configuração:

```yaml
app:
  security:
    jwt-secret-base64: ${JWT_SECRET_BASE64}
    issuer: gestor-de-vendas-api
    access-token-minutes: 15
```

O segredo deve ter pelo menos 32 bytes após Base64 decode; startup falha com mensagem clara se menor.

- [ ] **Step 4: Configurar Spring Security**

Permitir sem autenticação somente:

```text
GET  /actuator/health
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/invites/company/accept
POST /api/invites/user/accept
```

Demais `/api/**` exigem bearer token. CSRF pode ser ignorado para bearer endpoints, mas refresh/logout devem validar `Origin` permitido porque usam cookie.

- [ ] **Step 5: Rodar testes e commit**

Run: `mvn test`  
Expected: PASS.

```bash
git add .
git commit -m "feat: add jwt authentication foundation"
```

---

### Task 5: Login, refresh token, logout e `/me`

**Files:**
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/RefreshToken.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/RefreshTokenRepository.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/common/token/SecureTokenService.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/AuthService.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/AuthController.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/dto/LoginRequest.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/dto/AuthResponse.java`
- Test: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/auth/AuthFlowIntegrationTest.java`

**Interfaces:**
- `POST /api/auth/login` body `{email,password}`.
- `POST /api/auth/refresh` reads cookie `refresh_token`.
- `POST /api/auth/logout` revokes current refresh token and expires cookie.
- `GET /api/auth/me` returns identity, company context and role; platform admin may have `company=null`.

- [ ] **Step 1: Escrever integração de login feliz e bloqueios**

Cenários no mesmo teste de integração:

```text
senha válida + usuário/empresa ativos -> 200 + accessToken + Set-Cookie HttpOnly
senha errada -> 401 INVALID_CREDENTIALS
must_reset_password=true -> 403 PASSWORD_RESET_REQUIRED
usuario ativo=false -> 403 USER_BLOCKED
empresa ativa=false -> 403 COMPANY_BLOCKED
```

- [ ] **Step 2: Rodar e confirmar falha**

Run: `mvn test -Dtest=AuthFlowIntegrationTest`  
Expected: FAIL.

- [ ] **Step 3: Implementar `SecureTokenService`**

Gerar 32 bytes aleatórios com `SecureRandom`, Base64 URL-safe sem padding, e persistir somente:

```java
HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(UTF_8)))
```

- [ ] **Step 4: Implementar login com BCrypt**

`PasswordEncoder`:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

O login deve sempre retornar `INVALID_CREDENTIALS` para email inexistente ou senha errada, sem diferenciar os casos.

- [ ] **Step 5: Implementar refresh rotativo**

Ao usar refresh válido: revogar token atual, criar novo token/hash, emitir novo cookie e novo access token. Reuso de refresh já revogado deve revogar todas as sessões ativas daquele usuário e retornar `SESSION_INVALID`.

- [ ] **Step 6: Implementar `/me`**

Formato:

```json
{
  "user": {"id":"...","email":"...","platformAdmin":false},
  "company": {"id":"...","name":"Empresa","active":true},
  "role":"ADMIN"
}
```

- [ ] **Step 7: Rodar testes e commit**

Run: `mvn test -Dtest=AuthFlowIntegrationTest`  
Expected: PASS.

```bash
git add .
git commit -m "feat: add login refresh logout and me"
```

---

### Task 6: Rate limiting de autenticação

**Files:**
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/AuthRateLimiter.java`
- Modify: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/AuthController.java`
- Test: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/auth/AuthRateLimiterTest.java`

**Interfaces:**
- `checkLogin(ip, normalizedEmail)` permits 5 attempts/15 min by default.
- `checkPasswordReset(ip, normalizedEmail)` permits 5 requests/60 min by default.
- Error: HTTP 429 `RATE_LIMITED`.

- [ ] **Step 1: Escrever teste com relógio injetável**

```java
@Test
void blocksSixthLoginAttemptWithinWindow() {
    for (int i = 0; i < 5; i++) limiter.checkLogin("127.0.0.1", "a@b.com");
    ApiException ex = assertThrows(ApiException.class,
        () -> limiter.checkLogin("127.0.0.1", "a@b.com"));
    assertEquals("RATE_LIMITED", ex.code());
}
```

- [ ] **Step 2: Implementar contador em memória com `ConcurrentHashMap` e `Clock`**

Single-node VPS é a topologia V1; portanto o rate limit em memória é suficiente agora. Chaves expiradas devem ser removidas quando acessadas para impedir crescimento indefinido.

- [ ] **Step 3: Integrar em login e forgot-password, testar e commit**

Run: `mvn test -Dtest=AuthRateLimiterTest,AuthFlowIntegrationTest`  
Expected: PASS.

```bash
git add .
git commit -m "feat: rate limit authentication endpoints"
```

---

### Task 7: Redefinição de senha e Brevo

**Files:**
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/PasswordResetToken.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/PasswordResetTokenRepository.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/mail/EmailSender.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/mail/BrevoEmailSender.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/mail/MailProperties.java`
- Modify: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/AuthService.java`
- Modify: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/auth/AuthController.java`
- Test: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/auth/PasswordResetIntegrationTest.java`

**Interfaces:**
- `POST /api/auth/forgot-password` body `{email}` always returns 202.
- `POST /api/auth/reset-password` body `{token,newPassword}`.
- `EmailSender.sendPasswordReset(String email, String resetUrl)`.
- `EmailSender.sendCompanyInvite(String email, String inviteUrl)`.
- `EmailSender.sendUserInvite(String email, String inviteUrl)`.

- [ ] **Step 1: Escrever testes**

Cobrir:

```text
email existente -> 202 + um token persistido por hash + email fake chamado
email inexistente -> 202 + nenhum vazamento de existência
reset token válido -> senha BCrypt nova + must_reset_password=false + token usado + refresh sessions revogadas
reset token expirado/usado -> 400 RESET_TOKEN_INVALID
```

- [ ] **Step 2: Rodar para confirmar falha**

Run: `mvn test -Dtest=PasswordResetIntegrationTest`  
Expected: FAIL.

- [ ] **Step 3: Implementar `EmailSender` e Brevo via `RestClient`**

Request para Brevo deve enviar `api-key` de `${BREVO_API_KEY}` e usar remetente configurado:

```yaml
app:
  mail:
    from-email: ${MAIL_FROM_EMAIL}
    from-name: ${MAIL_FROM_NAME:Gestor de Vendas}
    app-base-url: ${APP_BASE_URL}
```

A interface deve ser explícita:

```java
public interface EmailSender {
    void sendPasswordReset(String email, String resetUrl);
    void sendCompanyInvite(String email, String inviteUrl);
    void sendUserInvite(String email, String inviteUrl);
}
```

Para reset, o corpo contém link `${APP_BASE_URL}/redefinir-senha?token=<raw>`. Para convites, usar `${APP_BASE_URL}/primeiro-acesso?token=<raw>&type=company` ou `type=user`. Nunca logar `<raw>`.

- [ ] **Step 4: Implementar política de senha mínima**

Validar no backend: mínimo 8 caracteres, ao menos uma letra e um número. Responder `WEAK_PASSWORD` sem retornar a senha.

- [ ] **Step 5: Rodar testes e commit**

Run: `mvn test -Dtest=PasswordResetIntegrationTest`  
Expected: PASS com `EmailSender` fake no profile de teste.

```bash
git add .
git commit -m "feat: add secure password reset flow"
```

---

### Task 8: Auditoria base

**Files:**
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/audit/AuditEvent.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/audit/AuditRepository.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/audit/AuditService.java`
- Test: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/audit/AuditServiceTest.java`

**Interfaces:**
- `record(String action, UUID companyId, UUID userId, String entityType, UUID entityId, String reason, Map<String,Object> metadata)`.
- Metadata sanitizer rejects keys containing `password`, `token`, `authorization`, `secret`.

- [ ] **Step 1: Escrever teste que impede segredo em auditoria**

```java
@Test
void rejectsSensitiveMetadataKeys() {
    assertThrows(IllegalArgumentException.class, () -> auditService.record(
        "TEST", companyId, userId, "USER", userId, null, Map.of("refreshToken", "raw")));
}
```

- [ ] **Step 2: Implementar `AuditService` e persistência JSONB**

Eventos iniciais: `PASSWORD_RESET`, `COMPANY_CREATED`, `COMPANY_BLOCKED`, `COMPANY_ACTIVATED`, `USER_INVITED`, `USER_BLOCKED`, `USER_UNBLOCKED`, `USER_ROLE_CHANGED`, `USER_REMOVED`.

- [ ] **Step 3: Rodar testes e commit**

Run: `mvn test -Dtest=AuditServiceTest`  
Expected: PASS.

```bash
git add .
git commit -m "feat: add secure audit foundation"
```

---

### Task 9: PLATFORM_ADMIN, empresas e convite inicial

**Files:**
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/invite/CompanyInvite.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/invite/CompanyInviteRepository.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/invite/InviteService.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/invite/InviteController.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/platform/PlatformService.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/platform/PlatformController.java`
- Test: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/platform/PlatformCompanyIntegrationTest.java`

**Interfaces:**
- `POST /api/platform/companies/invites` body `{name,legalName,document,ownerEmail,primaryColor,secondaryColor}`.
- `POST /api/platform/company-invites/{id}/resend`.
- `DELETE /api/platform/company-invites/{id}` cancels pending invite.
- `POST /api/platform/companies/{id}/block`.
- `POST /api/platform/companies/{id}/activate`.
- `GET /api/platform/companies`.
- Public `POST /api/invites/company/accept` body `{token,name,password}`.

- [ ] **Step 1: Escrever integração de autorização de plataforma**

```text
usuario comum -> /api/platform/** = 403
platform_admin -> cria convite = 201
aceite válido -> cria/atualiza usuario + vínculo OWNER + invalida convite
aceite repetido -> 400 INVITE_INVALID
block empresa -> próximo /api/auth/me do usuário da empresa = 403 COMPANY_BLOCKED
activate -> login volta a funcionar
```

- [ ] **Step 2: Implementar autorização global sem confundir com role de empresa**

Use authority `PLATFORM_ADMIN` derivada do registro atual do usuário no banco. Não permitir que claim JWT sozinho conceda autoridade após o usuário ter sido rebaixado; o serviço de plataforma deve confirmar `usuarios.platform_admin=true` antes de executar mutações.

- [ ] **Step 3: Implementar convite transacional**

Ao criar convite: reservar/criar empresa e convite pendente. Ao aceitar: criar usuário se necessário, exigir senha forte, criar vínculo `OWNER`, marcar convite `used_at`. Se e-mail já possuir vínculo em outra empresa na V1, retornar `USER_ALREADY_LINKED`.

- [ ] **Step 4: Implementar bloquear/ativar com revogação de sessões**

Bloquear empresa revoga todos os `refresh_tokens` ativos dos usuários vinculados e registra auditoria.

- [ ] **Step 5: Rodar testes e commit**

Run: `mvn test -Dtest=PlatformCompanyIntegrationTest`  
Expected: PASS.

```bash
git add .
git commit -m "feat: add platform company provisioning"
```

---

### Task 10: Usuários internos e matriz OWNER/ADMIN/VENDEDOR

**Files:**
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/invite/UserInvite.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/invite/UserInviteRepository.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/user/UserManagementService.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/user/UserManagementController.java`
- Create: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/tenant/TenantGuard.java`
- Test: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/user/UserPermissionsIntegrationTest.java`

**Interfaces:**
- `GET /api/users` OWNER/ADMIN.
- `POST /api/users/invites` OWNER/ADMIN; request `{email, role?}`.
- ADMIN invite always persisted as `VENDEDOR`, ignoring requested role.
- `PATCH /api/users/{membershipId}/status` OWNER/ADMIN body `{active}`.
- `PATCH /api/users/{membershipId}/role` OWNER only body `{role}`.
- `DELETE /api/users/{membershipId}` OWNER only; soft removal/inactive membership + session revocation.
- Public `POST /api/invites/user/accept` body `{token,name,password}`.

- [ ] **Step 1: Escrever matriz de testes**

Cobrir explicitamente:

```text
ADMIN convida role=ADMIN -> convite/aceite resulta VENDEDOR
OWNER convida ADMIN -> resulta ADMIN
ADMIN altera role -> 403
OWNER altera VENDEDOR -> ADMIN -> 200
ADMIN bloqueia VENDEDOR -> 200
ADMIN bloqueia OWNER -> 403
OWNER bloqueia outro usuário -> 200
ADMIN remove usuário -> 403
OWNER remove usuário -> 204 + refresh revogado
VENDEDOR lista/convida usuários -> 403
```

- [ ] **Step 2: Implementar `TenantGuard`**

Métodos explícitos:

```java
void requireOwner(TenantContext ctx);
void requireOwnerOrAdmin(TenantContext ctx);
void requireSameCompany(TenantContext ctx, CompanyMembership target);
```

Não espalhar comparações de strings de role pelos controllers.

- [ ] **Step 3: Impedir auto-bloqueio/remoção que deixe empresa sem OWNER**

Antes de bloquear/remover/rebaixar OWNER, contar owners ativos. Se seria o último, retornar `LAST_OWNER_REQUIRED`.

- [ ] **Step 4: Rodar testes e commit**

Run: `mvn test -Dtest=UserPermissionsIntegrationTest`  
Expected: PASS.

```bash
git add .
git commit -m "feat: enforce company user permissions"
```

---

### Task 11: Isolamento multiempresa bloqueador

**Files:**
- Test: `../gestor-de-vendas-api/src/test/java/com/gestordevendas/api/tenant/TenantIsolationIntegrationTest.java`
- Modify: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/tenant/TenantGuard.java`
- Modify: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/user/UserManagementService.java`
- Modify: `../gestor-de-vendas-api/src/main/java/com/gestordevendas/api/platform/PlatformService.java`

**Interfaces:**
- No new public API; this task proves the tenant boundary.

- [ ] **Step 1: Criar duas empresas com IDs e dados semelhantes**

Fixture deve criar `Empresa A`, `Empresa B`, um OWNER/ADMIN em cada e ao menos um vínculo alvo em cada empresa.

- [ ] **Step 2: Escrever ataques por ID manipulado**

```text
ADMIN A tenta bloquear membership de B -> 404 ou 403, sem revelar dados de B
OWNER A tenta alterar role de B -> bloqueado
PLATFORM_ADMIN consegue bloquear empresa B apenas via endpoint /platform
usuario de empresa bloqueada com JWT ainda válido -> operação protegida falha
```

- [ ] **Step 3: Rodar isoladamente e depois suite completa**

Run: `mvn test -Dtest=TenantIsolationIntegrationTest`  
Expected: PASS.

Run: `mvn test`  
Expected: PASS, zero testes ignorados nos fluxos de segurança.

- [ ] **Step 4: Commit**

```bash
git add .
git commit -m "test: lock down tenant isolation"
```

---

### Task 12: Docker image, CI e documentação local

**Files:**
- Create: `../gestor-de-vendas-api/Dockerfile`
- Create: `../gestor-de-vendas-api/.github/workflows/ci.yml`
- Create: `../gestor-de-vendas-api/README.md`
- Modify: `../gestor-de-vendas-api/.env.example`

**Interfaces:**
- Produces immutable application image.
- CI runs `mvn -B verify` on every push/PR.
- Production deployment is deliberately deferred to Plan 6; this task validates buildability only.

- [ ] **Step 1: Criar Dockerfile multi-stage**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/gestor-de-vendas-api-0.0.1-SNAPSHOT.jar app.jar
USER 10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

A imagem de build usa Maven apenas no estágio temporário; a imagem final contém somente o JRE e o JAR.

- [ ] **Step 2: Criar CI**

```yaml
name: api-ci
on:
  push:
  pull_request:
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - run: ./mvnw -B verify
```

- [ ] **Step 3: Documentar setup local**

README deve conter exatamente o fluxo operacional:

```bash
cp .env.example .env
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl http://localhost:8080/actuator/health
./mvnw verify
```

E listar env vars obrigatórias sem valores secretos: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET_BASE64`, `BREVO_API_KEY`, `MAIL_FROM_EMAIL`, `APP_BASE_URL`.

- [ ] **Step 4: Verificar pacote e imagem**

Run: `./mvnw -B verify`  
Expected: BUILD SUCCESS.

Run: `docker build -t gestor-de-vendas-api:local .`  
Expected: image builds successfully.

- [ ] **Step 5: Commit final do plano**

```bash
git add .
git commit -m "ci: verify backend build and tests"
```

---

## Phase 1 Acceptance Checklist

O plano só está concluído quando todos os itens abaixo forem verdadeiros:

- [ ] `mvn verify` passa com PostgreSQL real em Testcontainers.
- [ ] Banco nasce exclusivamente por Flyway; Hibernate está em `validate`.
- [ ] Login não diferencia e-mail inexistente de senha errada.
- [ ] Usuário/empresa bloqueados não operam mesmo com access token ainda válido.
- [ ] Refresh token é rotativo, revogável e persistido somente por hash.
- [ ] Reset de senha usa token de 30 min, uso único, persistido somente por hash.
- [ ] Usuário migrável com `must_reset_password=true` recebe `PASSWORD_RESET_REQUIRED` no login.
- [ ] Brevo está atrás de `EmailSender` e testes usam fake, sem chamadas externas.
- [ ] PLATFORM_ADMIN é separado de OWNER/ADMIN/VENDEDOR.
- [ ] ADMIN convida sempre como VENDEDOR.
- [ ] Apenas OWNER muda papéis e remove vínculo.
- [ ] OWNER/ADMIN bloqueiam/desbloqueiam sem permitir destruir o último OWNER.
- [ ] Teste de isolamento entre duas empresas passa.
- [ ] Erros não expõem stack trace, SQL, senha ou tokens.
- [ ] Auditoria rejeita metadata sensível.
- [ ] Docker image compila e CI executa a suite completa.

## Deferred to Following Plans

Este plano deliberadamente **não** implementa clientes, produtos, categorias, R2, compras, estoque, vendas, Prazo, relatórios, pedidos públicos, migração do Vue ou importação de dados. Esses módulos dependem da fundação criada aqui e serão tratados nos planos seguintes do roadmap.
