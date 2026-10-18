# EduERP Backend

Spring Boot backend for the EduERP application. It provides authentication, user management, course management, attendance, grades, schedules, messaging, and reporting APIs.

## Tech Stack

- Java 21
- Spring Boot 3.4
- Spring Security with JWT
- Spring Data JPA
- MySQL (`eduerp`) with JPA schema validation

## Run Locally

```powershell
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080` by default.

## Environment Variables

- `PORT`: Override the server port.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: MySQL connection values; use `DB_URL=jdbc:mysql://localhost:3306/edu_erp`.
- `JWT_SECRET`: required random signing secret; never commit it.
- `JWT_EXPIRATION_MS`: access token lifetime; defaults to 900000 ms.
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`: Gmail SMTP/App Password values used for OTP email.
- `FRONTEND_URL`: allowed frontend origin.

The default `DDL_AUTO=validate` does not create, alter, or delete tables. Flyway migrations are reviewed separately; verify the target `edu_erp` schema and Flyway history before starting.

## API Docs

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
