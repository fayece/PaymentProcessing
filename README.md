# Payment Processing System

> This project is not intended for production use. It is a learning exercise with a limited scope.
> Some limitations have been expressed in code comments in the form of TODOs. 

## About
A payment processing backend exploring the core concepts in financial systems: payment lifecycles, idempotency, and fraud detection.

## Roadmap
**Implemented**
- Payment lifecycle with idempotency
- Domain modeling and validation
- Full controller, service, and integration test coverage
- Azure deployment

**In Progress**
- Fraud flagging
- Event system

## API Documentation
The API documentation can be obtained in two ways:

### Direct link via Azure
https://payment-processing-server.yellowrock-ae6fb545.germanywestcentral.azurecontainerapps.io/swagger-ui/index.html

### Running the project locally
**Requires Docker to be installed. Ensure Docker is running, and you are running a terminal in the project root directory.**
```bash
cp .env.example .env
docker compose --env-file .env --profile dev up -d --build
```
Once running, the API docs are available at http://localhost:8092/swagger-ui/index.html

## Tech Stack
| **Layer**        | **Technology**           |
|------------------|--------------------------|
| **Language**     | Kotlin                   |
| **Framework**    | Spring Boot              |
| **Database**     | PostgreSQL               |
| **Migrations**   | Flyway                   |
| **Build Tool**   | Gradle (Kotlin DSL)      |
| **Java version** | JDK 21 (Eclipse Temurin) |
