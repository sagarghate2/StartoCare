# StartoCare User Service

A layered Spring Boot REST service for user registration, JWT login, and authenticated profile management.

## Stack and structure

- Java 17, Spring Boot 3.4.5, Maven
- Spring Web, Spring Security, Spring Data JPA, Bean Validation
- PostgreSQL with Flyway-managed schema migrations
- JJWT 0.12.6 and BCrypt password hashing
- JUnit 5, Mockito, Spring Security Test, and H2 for automated tests

```text
src/main/java/com/startocare/userservice/
  config/       Spring Security configuration
  controller/   REST endpoints
  dto/          Validated requests and safe response DTOs
  entity/       JPA user entity
  enums/        Role and companion verification states
  exception/    Consistent API error responses
  repository/   Spring Data JPA access
  security/     JWT creation, validation, and error handlers
  service/      Authentication and user business logic
src/main/resources/
  application.yml
  db/migration/ Versioned Flyway migrations
src/test/
  ...           H2-backed service and security integration tests
```

## Requirements

- JDK 17
- Maven 3.9+
- PostgreSQL
- VS Code with the Extension Pack for Java (recommended)

In the VS Code PowerShell terminal, verify Java and Maven:

```powershell
java -version
mvn -version
```

Both should report Java 17. If multiple JDKs are installed, set `JAVA_HOME` to the installed JDK 17 directory and put its `bin` directory first in `PATH`.

## PostgreSQL setup on Windows

Find and start the installed PostgreSQL Windows service (use its discovered service name):

```powershell
Get-Service *postgres*
$pgService = Get-Service -Name 'postgresql*' | Select-Object -First 1 -ExpandProperty Name
Start-Service -Name $pgService
```

Connect as a PostgreSQL administrator and create a dedicated database user and database. Replace the password placeholder locally; do not put real credentials in source control:

```powershell
psql -U postgres -h localhost
```

At the `psql` prompt:

```sql
CREATE USER startocare_user WITH PASSWORD 'replace_with_a_local_password';
CREATE DATABASE startocare_users OWNER startocare_user;
\q
```

Flyway creates and upgrades the tables at startup. The service uses its own `startocare_users` database; it must not share another microservice's database.

## Environment and run

Copy the safe template for reference:

```powershell
Copy-Item .env.example .env
```

Spring Boot does not load `.env` automatically. Set variables in the same PowerShell terminal used to launch Maven:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/startocare_users"
$env:DB_USERNAME = "startocare_user"
$env:DB_PASSWORD = Read-Host "PostgreSQL password"
$env:MAIL_HOST = "smtp.gmail.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = Read-Host "Gmail address"
$env:MAIL_PASSWORD = Read-Host "Google App Password"
$env:MAIL_SMTP_AUTH = "true"
$env:MAIL_SMTP_STARTTLS_ENABLE = "true"
$bytes = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$env:JWT_EXPIRATION_MS = "3600000"
mvn spring-boot:run
```

For Gmail, enable 2-Step Verification and create an App Password; use that 16-character password for `MAIL_PASSWORD`, not your normal Google password. `JWT_SECRET` must be Base64-encoded and decode to at least 32 bytes. Generate and securely manage a different secret per environment. Do not commit credentials or secrets. The service listens at `http://localhost:8082`.

The regular configuration uses `ddl-auto: validate` and Flyway migrations. For production, review and test each versioned migration against the deployed PostgreSQL version before rollout; do not use Hibernate `ddl-auto: update` in production. If a separate local-development profile later uses `ddl-auto: update`, keep it local-only and continue using migrations for production.

## Tests

```powershell
mvn clean test
```

Tests use the `test` profile and an in-memory H2 database, so PostgreSQL credentials are not required for tests.

## API

All errors have `timestamp`, `status`, `errorCode`, `message`, `path`, and `details` fields. Passwords and password hashes are never returned.

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/send-otp` | Public | Email a six-digit registration code |
| POST | `/api/auth/register` | Public | Create a FAMILY account and issue a signed JWT |
| POST | `/api/auth/login` | Public | Authenticate and issue a signed JWT |
| GET | `/api/users/me` | Bearer JWT | Get the authenticated user's profile |
| PATCH | `/api/users/me` | Bearer JWT | Update the user's full name |
| POST | `/api/auth/change-password` | Bearer JWT | Change password after verifying the current password |

Public registration ignores client-supplied role and status fields and always creates a `FAMILY` account. Phone numbers are required for new registrations and must use the Indian `+91` format; existing accounts can add or change a number through the authenticated profile update endpoint. Role/status updates and companion approval are intentionally not public APIs. No booking endpoint is implemented here; any future booking authorization must require a `COMPANION` account with `VERIFIED` status.

Request an OTP, then register with the emailed code:

```http
POST http://localhost:8082/api/auth/send-otp
Content-Type: application/json

{
  "email": "example.user@example.com"
}
```

```http
POST http://localhost:8082/api/auth/register
Content-Type: application/json

{
  "fullName": "Example User",
  "email": "example.user@example.com",
  "password": "A-Strong-Password-123",
  "phoneNumber": "+919876543210",
  "otp": "123456"
}
```

The email OTP expires after five minutes and can be used once. Registration requires a valid email, a nonblank name of up to 120 characters, an Indian mobile number in `+91` format (for example, `+919876543210`), the valid OTP, and a 12–72 character password with uppercase, lowercase, digit, and symbol characters. Emails are trimmed and normalized to lowercase. Successful registration returns a bearer JWT and user profile, just like login. Duplicate registration returns `409 Conflict`; invalid credentials return a generic `401 Unauthorized`.

Use the token returned by login as `Authorization: Bearer <token>` for protected routes. JWTs contain the user UUID as `sub`, the server-side role, a token version, issue time, and expiration. A successful password change increments the token version and immediately invalidates previously issued tokens. Tokens are otherwise valid until expiry; there is no logout/revocation endpoint or refresh-token flow in this initial service.

Import [user-service.http](./user-service.http) in VS Code's REST Client extension for example register, login, profile, profile-update, and password-change requests.

Existing accounts created before phone-number support may have no phone number until they update it through `PATCH /api/users/me`. Include `phoneNumber` in the update body to set or change it; it must use the Indian `+91` format.
