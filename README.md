UNIVERSIDAD TÉCNICA ESTATAL DE QUEVEDO
EQUIPO H
# PE-Unidad-III

## BIOPET

Sistema web de gestión veterinaria (Proyecto Fin de Curso). Este repositorio contiene la Práctica Experimental Unidad III: capa de integración ORM, caché con Redis (cache-aside) y arquitectura documentada con Modelo C4.

## Stack tecnológico

- Java 21
- Spring Boot 3.2.x
- Spring Security 6 + JWT (jjwt)
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Redis 7
- Flyway
- Angular 17+
- Docker / Docker Compose

## Estructura del repositorio

```
PE3/
├── backend/        Spring Boot (Java 21) — API REST BIOPET
├── frontend/        Angular — cliente web
├── infra/
│   ├── postgres/    Scripts de inicialización de Postgres (vacío por ahora, se usa Flyway)
│   └── redis/       Configuración de Redis
├── scripts/         Scripts técnicos (benchmark de caché, pendiente)
├── docs/            Documentación académica (ADR, arquitectura, informe) — gestionada por el equipo, no modificar aquí
├── .github/workflows/ci.yml
├── docker-compose.yml
├── .env.example
└── README.md
```

## Base técnica utilizada

Esta estructura se construyó a partir de dos avances previos del PFC:

- **PFC--VET-ENTR1B** (Java 21 + Spring Boot 3.2.12 + Angular 17.3 + JWT + Redis + Flyway + Docker Compose): base técnica seleccionada e integrada en `backend/` y `frontend/`. Ya implementa autenticación JWT stateless, blacklist de tokens y CRUD de Mascotas.
- **APP-WEB-PFC-**: usado solo como referencia de dominio veterinario (modelo de datos ampliado en `database/schema.sql`, con entidades como Cita, Historial_Clinico, Factura, Chat_Triage). Su stack (ASP.NET Core) no es compatible con los requisitos técnicos del PFC y no se reutilizó como código.

## Requisitos previos

- Java 21 (Temurin recomendado)
- Maven (o usar el wrapper `./mvnw` incluido en `backend/`)
- Node.js 20+ y npm (para `frontend/`)
- Docker Desktop + Docker Compose

## Variables de entorno

Copiar `.env.example` a `.env` y ajustar valores (ver archivo para la lista completa: credenciales de Postgres, puertos, `JWT_SECRET`, etc.). No subir `.env` al repositorio.

## Ejecución

### PostgreSQL y Redis (Docker)

```bash
cp .env.example .env
docker compose up -d postgres redis
```

### Backend (Spring Boot)

```bash
cd backend
./mvnw spring-boot:run      # Linux/Mac
.\mvnw.cmd spring-boot:run  # Windows
```

Pruebas:

```bash
cd backend
./mvnw test
```

### Frontend (Angular)

```bash
cd frontend
npm install
npm start
```

### Todo el stack con Docker Compose

```bash
docker compose up --build -d
```

- Frontend: http://localhost:4200
- Swagger UI: http://localhost:8080/api/swagger-ui.html
- Actuator Health: http://localhost:8080/actuator/health

## Autenticación (JWT en cookie HttpOnly)

- `POST /api/auth/login`: valida credenciales y responde con la cookie `BIOPET_ACCESS_TOKEN` (`HttpOnly`, `Path=/`, `SameSite=Lax`, `Secure` según `JWT_COOKIE_SECURE`). El JSON solo contiene id, nombre, email, rol y mensaje — nunca el token.
- `POST /api/auth/logout`: lee el JWT desde la cookie, guarda su `jti` en Redis (`jwt:blacklist:{jti}`) con TTL igual al tiempo restante de validez, y borra la cookie.
- `GET /api/auth/me`: devuelve el usuario autenticado a partir de la cookie (usado por Angular para restaurar sesión tras recargar la página).
- El frontend nunca lee `document.cookie` ni guarda el token; todas las peticiones HTTP usan `withCredentials` (centralizado en `credentialsInterceptor`).

## Requisitos técnicos pendientes (Unidad III)

Lo siguiente aún no está implementado en esta sesión; queda como trabajo técnico pendiente:

- CRUD completo con paginación (`Pageable`), ordenamiento dinámico y filtros múltiples para todas las entidades del dominio.
- `V3__datos_semilla.sql` con mínimo 50 registros realistas.
- Cache-aside con `@Cacheable` y `@CacheEvict` sobre la consulta principal.
- Pruebas de la capa Repository con JaCoCo, cobertura mínima 70%.
- Benchmark de listado: 10 repeticiones sin caché / 10 repeticiones con caché, promedio, P95 y speedup S = T_sin_cache / T_con_cache.

## Documentación académica

La documentación (ADR, diagramas C4, informe, referencias bibliográficas) se gestiona en `docs/` por el resto del equipo y no fue modificada en esta sesión.
