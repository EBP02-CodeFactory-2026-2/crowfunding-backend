# Crowdfunding Backend

Backend de una plataforma de crowdfunding, desarrollado en **Spring Boot** con autenticación **JWT** y persistencia en **PostgreSQL (Supabase)**.

Permite a los usuarios registrarse, autenticarse, crear proyectos de financiación colectiva y consultarlos públicamente. El modelo de datos ya contempla contribuciones y transacciones para futuras iteraciones.

> **Estado actual (Sprint 1):** implementados el registro/login de usuarios y el CRUD básico de proyectos (crear, listar, ver detalle). Las entidades `Contribution` y `Transaction` están modeladas en la base de datos, pero aún no tienen endpoints expuestos.

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
- **Project**: proyectos de financiación (título, descripción, meta de recaudación, fecha límite, estado).
- **Contribution**: aportes de un usuario a un proyecto (monto, estado: `PENDING`, `COMPLETED`, `REFUNDED`).
- **Transaction**: registro de la transacción asociada a una contribución.

El esquema completo está en [`database/schema-spint1.sql`](database/schema-spint1.sql), con datos de ejemplo en [`seed-data.sql`](database/seed-data.sql) y un script de limpieza en [`clear-db.sql`](database/clear-db.sql).

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

También se incluye una colección de Postman en [`docs/Crowdfunding API - Sprint 1.postman_collection.json`](docs/Crowdfunding%20API%20-%20Sprint%201.postman_collection.json) con ejemplos de las peticiones disponibles.

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
| POST   | `/`      | Sí (JWT)        | Crea un nuevo proyecto              |

> Los endpoints de lectura de proyectos son públicos; crear un proyecto y cualquier operación futura sobre contribuciones/transacciones requerirán un token JWT válido en el header `Authorization: Bearer <token>`.
