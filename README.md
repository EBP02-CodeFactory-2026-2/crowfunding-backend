# Crowdfunding Backend

Backend de una plataforma de crowdfunding, desarrollado en **Spring Boot** con autenticación **JWT** y persistencia en **PostgreSQL (Supabase)**.

Permite a los usuarios registrarse, autenticarse, crear proyectos de financiación colectiva (en estado borrador o activo), consultar campañas públicamente y realizar aportes económicos. El sistema incluye un motor de evaluación de riesgo automático y protección contra ataques de repetición.

> **Estado actual (Sprint 2 completado):** Se ha implementado el ciclo de vida completo de los proyectos (creación en borrador, edición, publicación con cálculo de riesgo y eliminación condicional). Además, se habilitó el módulo de aportes económicos con validación de montos mínimos y protección contra *replay attacks* mediante `nonce`.

## Tecnologías

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Swagger](https://img.shields.io/badge/Docs-Swagger%20UI-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)
![Lombok](https://img.shields.io/badge/Lombok-red?style=for-the-badge)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-multi--stage-2496ED?style=for-the-badge&logo=docker&logoColor=white)

- **Spring Boot 4.1.1** — Web MVC, Data JPA, Security, Validation, Actuator
- **JWT** (`jjwt`) para autenticación stateless
- **springdoc-openapi** — documentación interactiva vía Swagger UI
- **Docker** — build multi-stage para despliegue

## Arquitectura

El proyecto sigue una organización por capas dentro de `com.crowdfunding.backend`:

```
config/        Configuración de Spring (CORS, OpenAPI, Security)
controllers/   Controladores REST (Auth, Project)
contribution/   DTOs específicos para el flujo de aportes
entity/        Entidades JPA (User, Project, Contribution, Transaction)
login/         DTOs y filtro de autenticación JWT
register/      DTOs de registro de usuario
project/       DTOs de proyectos (request/response)
persistence/   Repositorios JPA
services/      Lógica de negocio
exception/     Excepciones personalizadas y manejo global de errores
```

La seguridad es **stateless**: cada request autenticado se valida mediante un `JwtAuthFilter`, sin sesiones de servidor.

## Modelo de datos

- **User**: usuarios registrados (nombre, email, contraseña hasheada).
- **Project**: proyectos de financiación (título, descripción, meta, fecha límite, estado `DRAFT`/`ACTIVE`/`FUNDED`/`FAILED`, y risk score).
- **Contribution**: aportes de un usuario a un proyecto (monto, estado: `PENDING`, `COMPLETED`, `REFUNDED`).
- **Transaction**: registro de la transacción asociada a una contribución, incluyendo un campo nonce único para prevenir ataques de repetición.

El esquema completo está en [`database/schema-spint1.sql`](database/schema-spint1.sql) y las actuaqlizaciones de Sprint 2 en [`database/schema-sprint2.sql`](database/schema-sprint2.sql). Incluye datos de ejemplo en [`seed-data.sql`](database/seed-data.sql) y un script de limpieza en [`clear-db.sql`](database/clear-db.sql).

## Requisitos previos

- Java 17 o superior (se recomienda **Java 21**, usado en el `Dockerfile`)
- Maven (o usar el wrapper `./mvnw` incluido)
- Una instancia de PostgreSQL accesible (por ejemplo, un proyecto de Supabase)
- Docker (opcional, para ejecutar en contenedor)

## Variables de entorno

Configura estas variables en tu entorno local antes de ejecutar la aplicación (por ejemplo en un archivo `.env`, sin subirlo al repositorio):

| Variable      | Descripción                                  |
|---------------|-----------------------------------------------|
| `DB_URL`      | URL de conexión a la base de datos PostgreSQL |
| `DB_USER`     | Usuario de la base de datos                   |
| `DB_PASSWORD` | Contraseña de la base de datos                |
| `JWT_SECRET`  | Clave secreta usada para firmar los tokens JWT|

## Instalación y ejecución

### Localmente con Maven

```bash
# Clonar el repositorio
git clone https://github.com/EBP02-CodeFactory-2026-2/crowfunding-backend
cd backend

# Ejecutar la aplicación (usa el wrapper de Maven)
./mvnw spring-boot:run
```

La aplicación quedará disponible en `http://localhost:8080`.

### Con Docker

```bash
# Construir la imagen (multi-stage: build con JDK 21, ejecución con JRE 21)
docker build -t crowdfunding-backend .

# Ejecutar el contenedor
docker run -p 8080:8080 \
  -e DB_URL=<tu-url> \
  -e DB_USER=<tu-usuario> \
  -e DB_PASSWORD=<tu-password> \
  -e JWT_SECRET=<tu-secreto> \
  crowdfunding-backend
```

## Documentación de la API

Con la aplicación corriendo, la documentación interactiva (Swagger UI) está disponible en:

```
http://localhost:8080/swagger-ui.html
```

También se incluyen colecciones de Postman en la carpeta `docs/` con ejemplo de las peticiones y scripts para guardar tokens automáticamente:
- [`docs/Crowdfunding API - Sprint 1.postman_collection.json`](docs/Crowdfunding%20API%20-%20Sprint%201.postman_collection.json)
- [`docs/Crowdfunding API - Sprint 2.postman_collection.json`](docs/Crowdfunding%20API%20-%20Sprint%202.postman_collection.json)

## Endpoints principales

### Autenticación — `/api/v1/auth` (públicos)

| Método | Endpoint    | Descripción                  |
|--------|-------------|-------------------------------|
| POST   | `/register` | Registra un nuevo usuario     |
| POST   | `/login`    | Inicia sesión y retorna un JWT|

### Proyectos — `/api/v1/projects`

| Método | Endpoint | Auth requerida | Descripción                        |
|--------|----------|-----------------|-------------------------------------|
| GET    | `/`      | No              | Lista todos los proyectos           |
| GET    | `/{id}`  | No              | Obtiene el detalle de un proyecto   |
| GET    | `/me`    | Sí (JWT)        | Lista los proyectos creados por el usuario autenticado   |
| POST   | `/`      | Sí (JWT)        | Crea un nuevo proyecto              |
| PUT    | `/{id}`  | Sí (JWT)        | Edita un proyecto (restringe meta/plazo si está ACTIVE y recalcula riesgo) |
| POST   | `/{id}/publish`      | Sí (JWT)        | Cambia el estado a ACTIVE y calcula el risk_score |
| DELETE   | `/{id}`      | Sí (JWT)        | Elimina un proyecto (solo si no tiene aportes) |

### Aportes — `/api/v1/projects/{id}/contributions`

| Método | Endpoint | Auth requerida | Descripción                        |
|--------|----------|-----------------|-------------------------------------|
| POST   | `/{id}/contributions`      | Sí (JWT)        | Registra un aporte (valida monto mínimo ≥ $10.000 y unicidad del nonce) |


> Todos los endpoints protegidos requieren un token JWT válido en el header: `Authorization: Bearer <token>`. El sistema valida la propiedad del recurso (IDOR prevention) en las operaciones de escritura y eliminación.
