# EduERP Backend

Spring Boot backend for the EduERP application. It provides authentication, user management, course management, attendance, grades, schedules, messaging, and reporting APIs.

## Tech Stack

- Java 21
- Spring Boot 3.4
- Spring Security with JWT
- Spring Data JPA
- H2 in-memory database

## Run Locally

```powershell
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080` by default.

## Environment Variables

- `PORT`: Override the server port.
- `CORS_ALLOWED_ORIGIN_PATTERNS`: Comma-separated allowed origin patterns for frontend hosts.

## Demo Credentials

- Admin: `rohithmannemofficial@gmail.com` / `password123`
- Teacher: `deepika.rao@eduerp.com` / `password123`
- Student: `sneha.kumar@eduerp.com` / `password123`

## API Docs

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
