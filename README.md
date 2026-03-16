# Life Enrichment App

A full-stack application for Assisted Living facilities — helping Life Enrichment Directors schedule activities, track resident participation, send notifications, and share memories with families.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2.x |
| Security | Spring Security + JWT (JJWT 0.12.x) |
| Database | PostgreSQL 16 + Flyway migrations |
| ORM | Spring Data JPA + Hibernate |
| File storage | AWS S3 (SDK v2) |
| Notifications | Twilio (SMS), Firebase FCM (Push), SendGrid (Email) |
| Documentation | SpringDoc OpenAPI 2.x (Swagger UI) |
| PDF export | iText 8 |
| CSV export | OpenCSV |
| Mapping | MapStruct + Lombok |
| Testing | JUnit 5, Spring Security Test, H2 (test profile) |
| DevOps | Docker, Docker Compose, GitHub Actions, AWS ECS Fargate |

---

## Getting Started (Local Development)

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker & Docker Compose

### 1. Clone and run

```bash
git clone https://github.com/your-org/life-enrichment-app.git
cd life-enrichment-app
docker-compose up --build
```

This starts:
- **Spring Boot app** → http://localhost:8080
- **PostgreSQL** → localhost:5432
- **LocalStack (S3)** → localhost:4566
- **MailHog (email UI)** → http://localhost:8025

### 2. Swagger UI

Once the app is running:

```
http://localhost:8080/swagger-ui.html
```

### 3. Run tests only

```bash
mvn test -Dspring.profiles.active=test
```

---

## Project Structure

```
src/main/java/com/lifeenrichment/
├── controller/        # REST API endpoints
├── service/           # Business logic
├── repository/        # Spring Data JPA
├── entity/            # JPA entities
├── dto/               # Request & Response DTOs
│   ├── request/
│   └── response/
├── security/
│   ├── jwt/           # JwtUtils, JwtAuthFilter
│   └── config/        # SecurityConfig
├── scheduler/         # @Scheduled jobs
├── config/            # S3, OpenAPI, etc.
└── exception/         # GlobalExceptionHandler, custom exceptions

src/main/resources/
├── application.yml            # Main config (env-var driven)
├── application-test.yml       # H2 test profile
├── db/migration/              # Flyway SQL scripts
└── templates/email/           # Thymeleaf email templates
```

---

## Environment Variables

| Variable | Description | Example |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/life_enrichment` |
| `DB_USERNAME` | DB username | `postgres` |
| `DB_PASSWORD` | DB password | *(secret)* |
| `JWT_SECRET` | JWT signing secret (256-bit min) | *(secret)* |
| `AWS_REGION` | AWS region | `us-east-1` |
| `AWS_ACCESS_KEY_ID` | AWS access key | *(secret)* |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key | *(secret)* |
| `S3_BUCKET_NAME` | S3 bucket for photos | `life-enrichment-photos` |
| `TWILIO_ACCOUNT_SID` | Twilio account SID | *(secret)* |
| `TWILIO_AUTH_TOKEN` | Twilio auth token | *(secret)* |
| `TWILIO_FROM_NUMBER` | Twilio sender number | `+15005550006` |
| `MAIL_HOST` | SMTP host | `smtp.sendgrid.net` |
| `MAIL_PASSWORD` | SMTP password / API key | *(secret)* |

> **Never** put secrets in code or commit `.env` files. Use AWS Secrets Manager in production.

---

## CI/CD

| Trigger | Workflow | Action |
|---|---|---|
| Pull request to `main` or `develop` | `ci.yml` | Build + run all tests |
| Push to `main` | `deploy-staging.yml` | Build Docker image → push to ECR → deploy to ECS staging |
| Manual approval | `deploy-production.yml` (TBD) | Deploy to production ECS |

---

## Epics (Linear)

| # | Epic | Linear ID |
|---|---|---|
| 1 | User & Auth Management | 120-5 |
| 2 | Resident Management | 120-6 |
| 3 | Activity Scheduling | 120-7 |
| 4 | Attendance & Assistance Tracking | 120-8 |
| 5 | Notifications & Reminders | 120-9 |
| 6 | Photo & Memory Sharing | 120-10 |
| 7 | Family Portal | 120-11 |
| 8 | Reporting & Analytics | 120-12 |
| 9 | Infrastructure & DevOps | 120-13 |
