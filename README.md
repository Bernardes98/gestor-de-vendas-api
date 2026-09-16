# Gestor de Vendas API

Backend próprio do **Gestor de Vendas**, criado em Java 21 + Spring Boot 3, com PostgreSQL, Flyway, JWT/refresh token e isolamento multiempresa.

## Requisitos

- Java 21
- Docker (para PostgreSQL local e testes Testcontainers)
- Internet na primeira execução do `mvnw`, caso Maven não esteja instalado

## Configuração local

Copie as variáveis de exemplo:

```bash
cp .env.example .env
```

No Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

As variáveis obrigatórias são:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET_BASE64`
- `BREVO_API_KEY`
- `MAIL_FROM_EMAIL`
- `APP_BASE_URL`

Também podem ser configuradas `MAIL_FROM_NAME` e `APP_ALLOWED_ORIGINS`.

Gere `JWT_SECRET_BASE64` a partir de pelo menos 32 bytes aleatórios. O valor nunca deve ser commitado.

## Executar localmente

```bash
cp .env.example .env
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl http://localhost:8080/actuator/health
./mvnw verify
```

No Windows, substitua `./mvnw` por `mvnw.cmd`.

O profile `local` usa, por padrão, PostgreSQL em `localhost:5432`, banco `gestor`, usuário `gestor` e senha `gestor_local`. Para outros valores, exporte as variáveis de ambiente antes de iniciar a aplicação.

> O Spring Boot não lê `.env` automaticamente. O arquivo existe como referência e pode ser carregado pela sua IDE, terminal, Docker Compose ou gerenciador de segredos.

## Segurança definida nesta fase

- Access token JWT com 15 minutos.
- Refresh token com 30 dias, rotativo e persistido apenas por hash SHA-256.
- Cookie de refresh `HttpOnly`, `SameSite=Lax` e `Secure` fora do profile local.
- JWT não contém `empresa_id` nem papel da empresa; o tenant é resolvido no PostgreSQL a cada operação protegida.
- Empresas e usuários bloqueados deixam de operar mesmo com JWT ainda válido.
- `PLATFORM_ADMIN` é separado de `OWNER`, `ADMIN` e `VENDEDOR`.
- Senhas são BCrypt com custo 12.
- Tokens de convite e redefinição nunca são persistidos em texto puro.
- Respostas de erro usam `code`, `message` e `requestId`.

## Banco e migrations

O schema é criado exclusivamente pelo Flyway. Hibernate roda com `ddl-auto=validate`.

A migration inicial cria:

- `empresas`
- `usuarios`
- `empresa_usuarios`
- `refresh_tokens`
- `password_reset_tokens`
- `convites_empresa`
- `convites_usuario`
- `auditoria`

## Testes

```bash
./mvnw verify
```

Os testes de integração utilizam PostgreSQL 17 real via Testcontainers, portanto Docker precisa estar em execução.

## Build da imagem

```bash
./mvnw -B verify
docker build -t gestor-de-vendas-api:local .
```

O deploy em VPS/Caddy será tratado na etapa final de infraestrutura. Nesta fase, o objetivo é manter uma imagem reproduzível e CI executando toda a suíte.
