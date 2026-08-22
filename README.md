# AuthService

A secure authentication and authorization backend built with **Spring Boot**, providing user registration, email verification, JWT-based authentication, refresh-token rotation, password reset, session management, and security-focused rate limiting.

## Features

* User registration with email verification
* Secure password hashing
* JWT-based authentication
* Short-lived access tokens
* Refresh-token rotation and reuse detection
* Secure `HttpOnly` refresh-token cookies
* Logout and logout-all session revocation
* Password reset with single-use, expiring tokens
* Redis-based rate limiting and login protection
* Verification-email resend cooldown
* Email outbox with retry support
* PostgreSQL persistence
* Flyway database migrations
* CORS configuration
* Global exception handling with standardized error responses
* Spring Boot Actuator health and application information endpoints

## Tech Stack

* **Java**
* **Spring Boot**
* **Spring Security**
* **Spring Data JPA / Hibernate**
* **PostgreSQL**
* **Redis**
* **JWT**
* **Flyway**
* **JavaMailSender / SMTP**
* **Maven**

## Architecture

```text
                         ┌──────────────────┐
                         │      Client      │
                         └────────┬─────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │   AuthService    │
                         │   Spring Boot    │
                         └────────┬─────────┘
                                  │
                ┌─────────────────┼─────────────────┐
                ▼                 ▼                 ▼
        ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
        │  PostgreSQL  │  │    Redis     │  │ SMTP / Email │
        │ Users/Tokens │  │ Rate Limits  │  │    Outbox    │
        │   /Sessions  │  │   /Cooldowns │  │              │
        └──────────────┘  └──────────────┘  └──────────────┘
```

### Authentication Flow

1. User registers with email and password.
2. The account receives a verification email.
3. After verification, the user can authenticate.
4. Login returns a short-lived JWT access token and a refresh token stored in a secure `HttpOnly` cookie.
5. Refresh tokens are rotated on refresh.
6. Logout or token reuse detection can revoke the corresponding session.
7. Password reset uses a time-limited, single-use token.

## Security

The service applies several security controls:

* Passwords are securely hashed and never stored in plaintext.
* Verification, password-reset, and refresh tokens are stored using hashed representations where applicable.
* Refresh tokens are rotated to reduce replay risk.
* Refresh-token reuse detection invalidates compromised sessions.
* Login protection locks authentication attempts after repeated failures.
* Redis provides authentication endpoint rate limiting.
* Verification-email resend requests have a cooldown.
* Password-reset tokens expire and are single-use.
* Sensitive authentication cookies use `HttpOnly` and `Secure` attributes.
* Authentication responses are designed to avoid unnecessary account enumeration.
* CORS is explicitly configured rather than allowing unrestricted origins.

## Database

**PostgreSQL** is used for persistent application data.

Database schema changes are managed through **Flyway migrations**, allowing the database structure to evolve consistently across environments.

## Redis

Redis is used for short-lived security and throttling state, including:

* Authentication endpoint rate limiting
* Login attempt tracking
* Temporary account/login locks
* Email verification resend cooldowns

## Email Delivery

Email operations use an **outbox-based approach**.

Instead of making authentication requests depend directly on successful email delivery, email events are persisted and processed asynchronously with retry support.

This improves reliability when the SMTP provider is temporarily unavailable.

## Configuration

Create the required environment variables for your local environment.

Example:

```env
DATABASE_URL=jdbc:postgresql://localhost:5432/authservice
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_password

REDIS_HOST=localhost
REDIS_PORT=6379

JWT_SECRET=your_secure_secret

MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=your_username
MAIL_PASSWORD=your_password
```

Use strong, environment-specific secrets in production. Do not commit credentials or secrets to the repository.

## Running Locally

### Prerequisites

* Java
* Maven
* PostgreSQL
* Redis

Start PostgreSQL and Redis, configure the required environment variables, then run:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

Flyway migrations are applied automatically during application startup.

## Testing

Run the test suite with:

```bash
./mvnw test
```

## API Endpoints

Base URL:

```text
/api/auth
```

### Authentication

| Method | Endpoint                         | Description                                      | Authentication |
| ------ | -------------------------------- | ------------------------------------------------ | -------------- |
| `POST` | `/api/auth/signup`               | Register a new user                              | Public         |
| `GET`  | `/api/auth/verify?token={token}` | Verify user's email address                      | Public         |
| `POST` | `/api/auth/resend-verification`  | Resend email verification link                   | Public         |
| `POST` | `/api/auth/login`                | Authenticate user and issue tokens               | Public         |
| `POST` | `/api/auth/refresh`              | Refresh the access token using the refresh token | Public         |
| `POST` | `/api/auth/logout`               | Revoke the current refresh-token session         | Authenticated  |
| `POST` | `/api/auth/logout-all`           | Revoke all active sessions for the user          | Authenticated  |

### Password Management

| Method | Endpoint                    | Description                              | Authentication |
| ------ | --------------------------- | ---------------------------------------- | -------------- |
| `POST` | `/api/auth/forgot-password` | Request a password-reset email           | Public         |
| `POST` | `/api/auth/reset-password`  | Reset password using a valid reset token | Public         |

### Example Requests

#### Register

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "your-password"
}
```

#### Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "email": "user@example.com",
  "password": "your-password"
}
```

#### Refresh Token

```http
POST /api/auth/refresh
```

The refresh token is handled through the secure `HttpOnly` cookie.

#### Logout

```http
POST /api/auth/logout
Authorization: Bearer <access-token>
```

#### Logout All Sessions

```http
POST /api/auth/logout-all
Authorization: Bearer <access-token>
```

#### Email Verification

The verification link sent to the user's email points to:

```http
GET /api/auth/verify?token=<verification-token>
```

#### Forgot Password

```http
POST /api/auth/forgot-password
Content-Type: application/json
```

```json
{
  "email": "user@example.com"
}
```

#### Reset Password

```http
POST /api/auth/reset-password
Content-Type: application/json
```

```json
{
  "token": "<reset-token>",
  "newPassword": "new-password"
}
```

## Authentication Model

The API uses a **short-lived JWT access token** for authenticated requests.

Refresh tokens are maintained separately and rotated during token refresh. The refresh token is stored in a secure `HttpOnly` cookie to reduce exposure to client-side JavaScript.

```text
Login
  │
  ├── Access Token (JWT)
  │       └── Authorization: Bearer <token>
  │
  └── Refresh Token
          └── HttpOnly Cookie
```

## Project Goals

The primary goal of AuthService is to provide a **production-oriented authentication backend** with strong security defaults, reliable email workflows, session management, and protection against common authentication attacks.

## License

This project is intended for educational and portfolio use.
