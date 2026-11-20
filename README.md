# Crowdfunding Backend

Backend de una plataforma de crowdfunding, desarrollado en **Spring Boot** con autenticación **JWT** y persistencia en **PostgreSQL (Supabase)**.

Permite a los usuarios registrarse, autenticarse, crear proyectos de financiación colectiva (en estado borrador o activo), consultar campañas públicamente, realizar aportes económicos simulados, consultar su historial y comprobantes, y recibir notificaciones cuando un proyecto se cierra. El sistema incluye un motor de evaluación de riesgo automático, protección contra ataques de repetición (`nonce`) y un job programado que cierra proyectos vencidos y reembolsa aportes.

> **Estado actual (Sprint 3):** Se implementaron el historial de aportes con filtro por estado, el comprobante de aporte con medio de pago enmascarado, el filtro de proyectos por etiqueta de riesgo, la advertencia obligatoria para proyectos de riesgo alto, las notificaciones (`PROJECT_FUNDED`, `PROJECT_FAILED`, `REFUND_PROCESSED`) y el cierre automático de proyectos con reembolsos.

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

## Estructura del repositorio

```text
backend/       Código fuente de la API (Maven, Java 21)
database/      Scripts SQL: esquema por sprint, datos semilla y limpieza
docs/          Documentación funcional y colecciones de Postman
Dockerfile     Imagen multi-stage (se construye desde la raíz del repositorio)
README.md
```

## Arquitectura

El proyecto sigue una organización por capas dentro de `backend/src/main/java/com/crowdfunding/backend`:

```text
config/         Configuración de Spring (Security + CORS, OpenAPI, Scheduler)
controllers/    Controladores REST (Auth, Project, Contribution, Notification)
contribution/   DTOs del flujo de aportes (request, response, historial, comprobante)
entity/         Entidades JPA (User, Project, Contribution, Transaction, Notification) y enums de estado
login/          DTOs de login, UserAuthDetails y filtro de autenticación JWT
register/       DTOs de registro de usuario
project/        DTOs de proyectos (request/response)
notification/   DTO de notificaciones
persistence/    Repositorios JPA
services/       Lógica de negocio (auth, proyectos, scoring, aportes, cierre, notificaciones)
interfaces/     Contratos (ScoringEngine)
utils/          Utilidades (RiskUtils)
exception/      Excepciones personalizadas y manejo global de errores
```

La seguridad es **stateless**: cada request autenticado se valida mediante un `JwtAuthFilter`, sin sesiones de servidor.
Un `@Scheduled` (`ProjectClosureService`) corre cada 5 minutos para cerrar proyectos vencidos.

## Modelo de datos

- **User**: usuarios registrados (nombre, email, contraseña hasheada con BCrypt).
- **Project**: proyectos de financiación (título, descripción, meta, fecha límite, estado `DRAFT`/`ACTIVE`/`FUNDED`/`FAILED`/`DELETED`, y risk score).
- **Contribution**: aportes de un usuario a un proyecto (monto, estado: `PENDING`, `COMPLETED`, `REFUNDED`, `CANCELLED`).
- **Transaction**: registro de la transacción asociada a una contribución, incluyendo un campo `nonce` único para prevenir ataques de repetición.
- **Notification**: alertas para usuarios (`PROJECT_FUNDED`, `PROJECT_FAILED`, `REFUND_PROCESSED`).

## Requisitos previos

- Java 17 o superior (se recomienda **Java 21**, usado en el `Dockerfile`)
- Maven (o usar el wrapper `./mvnw` incluido)
- Una instancia de PostgreSQL accesible (por ejemplo, un proyecto de Supabase)
- Docker (opcional, para ejecutar en contenedor)

## Variables de entorno

La aplicación lee estas variables desde `application.properties` y **no carga archivos `.env` por sí sola**: deben estar definidas en el entorno del proceso (o pasarse con `--env-file` en Docker).

Configura estas variables en tu entorno local antes de ejecutar la aplicación (por ejemplo en un archivo `.env`, sin subirlo al repositorio):

| Variable      | Descripción                                  |
|---------------|-----------------------------------------------|
| `DB_URL`      | URL de conexión a la base de datos PostgreSQL |
| `DB_USER`     | Usuario de la base de datos                   |
| `DB_PASSWORD` | Contraseña de la base de datos                |
| `JWT_SECRET`  | Clave secreta usada para firmar los tokens JWT|

Para generar un `JWT_SECRET` válido:

```bash
openssl rand -base64 32
```

Valores fijos en `application.properties`: puerto `8080`, expiración del token `86400` segundos (24 h) y `spring.jpa.hibernate.ddl-auto=validate`.

# Base de datos

Como Hibernate solo **valida** el esquema (`ddl-auto=validate`), las tablas deben existir antes de arrancar la aplicación. Ejecuta los scripts de [`database/`](database/) en este orden:

1. [`schema-sprint1.sql`](database/schema-sprint1.sql): tablas `users`, `projects`, `contributions`, `transactions`.
2. [`schema-sprint2.sql`](database/schema-sprint2.sql): `risk_score`, meta y plazo opcionales en borradores, `nonce` en transacciones.
3. [`schema-sprint3.sql`](database/schema-sprint3.sql): tabla `notifications` e índices.
4. (Opcional) [`seed-data.sql`](database/seed-data.sql): datos de prueba
5. (Antes de un despliegue a producción) [`clear-db.sql`](database/clear-db.sql): elimina todos los datos de prueba y reinicia los identificadores.

## Instalación y ejecución

### Localmente con Maven

```bash
# Clonar el repositorio
git clone https://github.com/EBP02-CodeFactory-2026-2/crowfunding-backend
cd crowfunding-backend/backend

# Definir las variables de entorno (Linux/macOS)
export DB_URL="jdbc:postgresql://<host>:5432/<base>"
export DB_USER="<usuario>"
export DB_PASSWORD="<password>"
export JWT_SECRET="<secreto-en-base64>"

# Ejecutar la aplicación (usa el wrapper de Maven)
./mvnw spring-boot:run
```

En PowerShell las variables se definen con `$env:DB_URL = "..."`, y el wrapper se invoca como `.\mvnw.cmd spring-boot:run`.

La aplicación quedará disponible en `http://localhost:8080`. Para compilar sin ejecutar las pruebas: `./mvnw clean package -DskipTests` (la prueba `contextLoads` necesita una base de datos accesible y las variables de entorno definidas).

### Con Docker

El `Dockerfile` está en la raíz y copia la carpeta `backend/`, por lo que el build se ejecuta desde la raíz del repositorio:

```bash
# Construir la imagen (multi-stage: build con JDK 21, ejecución con JRE 21)
docker build -t crowdfunding-backend .
 
# Ejecutar el contenedor
docker run -p 8080:8080 \
  -e DB_URL=<tu-url-jdbc> \
  -e DB_USER=<tu-usuario> \
  -e DB_PASSWORD=<tu-password> \
  -e JWT_SECRET=<tu-secreto-base64> \
  crowdfunding-backend
```

También puedes usar un archivo con formato `CLAVE=valor` (sin comillas): `docker run -p 8080:8080 --env-file .env crowdfunding-backend`.

El contenedor corre en UTC. El campo `deadline` de los proyectos se maneja sin zona horaria y se compara con el reloj local del servidor.

## Documentación de la API

Con la aplicación corriendo, la documentación interactiva (Swagger UI) está disponible en:

```
http://localhost:8080/swagger-ui.html
```

La especificación OpenAPI está en `http://localhost:8080/v3/api-docs`.

Las colecciones de Postman están en la carpeta `docs/`, con ejemplos de las peticiones, variables de colección y scripts que guardan tokens e identificadores automáticamente:

- [`docs/Crowdfunding API - Sprint 3.postman_collection.json`](docs/Crowdfunding%20API%20-%20Sprint%203.postman_collection.json): **colección vigente**, cubre todos los endpoints actuales con pruebas de códigos HTTP.
- [`docs/Crowdfunding API - Sprint 1.postman_collection.json`](docs/Crowdfunding%20API%20-%20Sprint%201.postman_collection.json) y [`docs/Crowdfunding API - Sprint 2.postman_collection.json`](docs/Crowdfunding%20API%20-%20Sprint%202.postman_collection.json): versiones históricas.

Para usar la colección del Sprint 3: impórtala en Postman, define `baseUrl` (por defecto `http://localhost:8080`) y ejecuta en orden las carpetas Auth, Projects, Contributions y Notifications. La carpeta "Cierre automático (job)" requiere esperar al job programado (ver su descripción).

## Endpoints principales

### Autenticación — `/api/v1/auth` (públicos)

| Método | Endpoint    | Descripción                                          |
|--------|-------------|------------------------------------------------------|
| POST   | `/register` | Registra un nuevo usuario (responde 200)             |
| POST   | `/login`    | Inicia sesión y retorna un JWT. Bloquea el correo 15 min tras 5 intentos fallidos en 10 min |

### Proyectos — `/api/v1/projects`
 
| Método | Endpoint        | Auth requerida | Descripción                                                          |
|--------|-----------------|----------------|----------------------------------------------------------------------|
| GET    | `/`             | No             | Lista los proyectos `ACTIVE`; filtro opcional `?riskTag=ALTO,MEDIO,BAJO` |
| GET    | `/{id}`         | No             | Obtiene el detalle de un proyecto (sin filtrar por estado)           |
| GET    | `/me`           | Sí (JWT)       | Lista los proyectos creados por el usuario autenticado               |
| POST   | `/`             | Sí (JWT)       | Crea un proyecto en estado `DRAFT` (`title` y `description` obligatorios) |
| PUT    | `/{id}`         | Sí (JWT)       | Edita un proyecto. Fuera de `DRAFT` exige enviar `fundingGoal` y `deadline`; en `ACTIVE` recalcula el riesgo si cambia título, descripción o imagen |
| POST   | `/{id}/publish` | Sí (JWT)       | Pasa de `DRAFT` a `ACTIVE` y calcula el `risk_score`                 |
| DELETE | `/{id}`         | Sí (JWT)       | Eliminación lógica (`DELETED`); solo si no tiene aportes `COMPLETED` |

### Aportes — `/api/v1`

| Método | Endpoint                          | Auth requerida | Descripción                                                              |
|--------|-----------------------------------|----------------|--------------------------------------------------------------------------|
| POST   | `/projects/{id}/contributions`    | Sí (JWT)       | Registra un aporte (monto mínimo $10.000, `nonce` único, confirmación de riesgo en proyectos de "Riesgo Alto") |
| GET    | `/contributions/me`               | Sí (JWT)       | Historial de aportes del usuario; filtro opcional `?status=COMPLETED`    |
| GET    | `/contributions/me/{id}/receipt`  | Sí (JWT)       | Comprobante del aporte (medio de pago enmascarado)                       |

### Notificaciones — `/api/v1/notifications`

| Método | Endpoint      | Auth requerida | Descripción                                        |
|--------|---------------|----------------|----------------------------------------------------|
| GET    | `/me`         | Sí (JWT)       | Lista las notificaciones del usuario autenticado   |
| PUT    | `/{id}/read`  | Sí (JWT)       | Marca una notificación como leída (responde 204)   |

> Todos los endpoints protegidos requieren un token JWT válido en el header: `Authorization: Bearer <token>`. Sin token válido, Spring Security responde `403 Forbidden`. El sistema valida la propiedad del recurso (IDOR prevention) en las operaciones de escritura, eliminación, recibos y notificaciones.

## Cierre automático de proyectos
 
Cada 5 minutos, `ProjectClosureService` revisa los proyectos `ACTIVE` cuyo plazo ya venció: si alcanzaron la meta pasan a `FUNDED`; si no, pasan a `FAILED`, sus aportes `COMPLETED` pasan a `REFUNDED` y se generan las notificaciones correspondientes. Un proyecto que alcanza su meta antes del plazo sigue `ACTIVE` hasta el primer ciclo del job posterior a su `deadline`.