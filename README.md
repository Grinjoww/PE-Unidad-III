# UNIVERSIDAD TÉCNICA ESTATAL DE QUEVEDO

## EQUIPO H — PE-Unidad-III

# BIOPET

BIOPET es un sistema web de gestión veterinaria desarrollado como Proyecto Final de Curso de Aplicaciones Web. Implementa autenticación con JWT en cookie HttpOnly, CRUD de mascotas, persistencia con Hibernate/JPA, migraciones Flyway, caché con Redis y frontend Angular.

## Integrantes

- CARVAJAL LOOR JOHAN STALIN
- FAJARDO MONTES MICHAEL XAVIER
- MARISCAL CABRERA JAIME JOSUE

## Estado del proyecto

El proyecto se encuentra implementado y funcional.

Incluye:

- Login y logout con JWT en cookie HttpOnly.
- Blacklist de JTI en Redis.
- CRUD completo de mascotas.
- Paginación, ordenamiento y filtros múltiples.
- Migraciones versionadas con Flyway.
- 58 mascotas y 21 usuarios de prueba.
- Caché Redis con patrón cache-aside.
- Invalidación de caché al crear, editar o eliminar.
- 53 pruebas automatizadas aprobadas.
- Cobertura JaCoCo superior al 70 %.
- Benchmark con y sin caché.
- Diagramas C4, secuencias, ADR y evidencias.
- Integración continua con GitHub Actions.

## Stack tecnológico

- Java 21
- Spring Boot 3.2.12
- Spring Security 6
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Redis 7
- Flyway
- JWT con JJWT
- Angular 17.3
- TypeScript
- Maven
- JaCoCo
- Docker y Docker Compose
- GitHub Actions

## Arquitectura

BIOPET utiliza una arquitectura monolítica organizada por capas:

```text
Frontend Angular
       ↓
Controller
       ↓
Service
       ↓
Repository
       ↓
PostgreSQL
```

Redis se utiliza para:

- Caché del listado de mascotas.
- Blacklist de tokens JWT cerrados mediante su JTI.

## Estructura del repositorio

```text
PE3/
├── backend/                  API REST con Spring Boot
├── frontend/                 Cliente web Angular
├── infra/                    Configuración de infraestructura
├── scripts/                  Caché, logout, benchmark y cobertura
├── docs/
│   ├── adr/
│   ├── arquitectura/
│   ├── secuencias/
│   ├── informe/
│   └── evidencias/
├── .github/workflows/ci.yml
├── docker-compose.yml
├── .env.example
└── README.md
```

## Requisitos previos

- Java 21
- Node.js 20 o superior
- npm
- Docker Desktop
- Docker Compose
- Git

## Variables de entorno

Crear `.env` a partir de `.env.example`.

En Windows:

```powershell
Copy-Item .env.example .env
```

En Linux o macOS:

```bash
cp .env.example .env
```

El archivo `.env` contiene credenciales de PostgreSQL, puertos, configuración JWT, CORS y TTL de caché. No debe subirse al repositorio.

## Ejecución local

### 1. Iniciar PostgreSQL y Redis

Desde la raíz:

```powershell
docker compose up -d postgres redis
docker compose ps
```

PostgreSQL y Redis deben aparecer como `healthy`.

Puertos predeterminados:

```text
PostgreSQL: 5432
Redis: 6379
```

### 2. Iniciar el backend

Windows:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Linux o macOS:

```bash
cd backend
./mvnw spring-boot:run
```

Backend:

```text
http://localhost:8080
```

### 3. Iniciar el frontend

```powershell
cd frontend
npm ci
npm start
```

Frontend:

```text
http://localhost:4200
```

Angular utiliza `proxy.conf.json` para redirigir:

```text
/api → http://localhost:8080
```

### 4. Ejecutar todo con Docker

```powershell
docker compose up --build -d
```

Para detener los servicios:

```powershell
docker compose down
```

## Autenticación

### Login

```http
POST /api/auth/login
```

Ejemplo de cuerpo:

```json
{
  "email": "usuario@correo.ec",
  "password": "contraseña"
}
```

El backend genera la cookie:

```text
BIOPET_ACCESS_TOKEN
```

Características:

- HttpOnly.
- SameSite=Lax.
- Path=/.
- Secure configurable.
- El JWT no se devuelve en el JSON.
- Angular utiliza `withCredentials`.

### Usuario autenticado

```http
GET /api/auth/me
```

### Logout

```http
POST /api/auth/logout
```

El backend guarda el JTI del token en Redis con el tiempo restante de validez y elimina la cookie. Una reutilización posterior del token devuelve HTTP 401.

## API de mascotas

### Listar

```http
GET /api/mascotas
```

Parámetros:

```text
page
size
sort
nombre
especie
raza
```

Ejemplo:

```http
GET /api/mascotas?page=0&size=10&sort=nombre,asc&especie=Perro
```

### Crear

```http
POST /api/mascotas
```

### Actualizar

```http
PUT /api/mascotas/{id}
```

### Eliminar

```http
DELETE /api/mascotas/{id}
```

## Base de datos y Flyway

Migraciones:

```text
backend/src/main/resources/db/migration/
├── V1__schema_inicial.sql
├── V2__ajustes_modelo_mascotas.sql
└── V3__datos_semilla.sql
```

Datos cargados:

- 1 administrador.
- 20 usuarios con rol dueño.
- 58 mascotas.
- Relación de un usuario con muchas mascotas.

Tablas principales:

```text
usuarios
mascotas
flyway_schema_history
```

## Caché Redis

Nombre de la caché:

```text
mascotas-listado
```

TTL:

```text
300 segundos
```

La clave distingue:

```text
page, size, sort, nombre, especie y raza
```

Ejemplo:

```text
mascotas:listado:page=0:size=10:sort=id,asc:nombre=:especie=:raza=
```

Consulta cacheada:

```java
@Cacheable(
    cacheNames = CacheConfig.CACHE_MASCOTAS_LISTADO,
    key = "#root.target.claveListado(#nombre, #especie, #raza, #pageable)"
)
```

Invalidación:

```java
@CacheEvict(
    cacheNames = CacheConfig.CACHE_MASCOTAS_LISTADO,
    allEntries = true
)
```

Todas las entradas del listado se eliminan al crear, actualizar o eliminar una mascota.

## Scripts técnicos

### Verificación de caché

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
-File .\scripts\verificar-cache.ps1
```

Comprueba cache miss, cache hit, TTL e invalidación.

### Verificación de logout

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
-File .\scripts\verificar-logout.ps1
```

Comprueba login, cookie HttpOnly, logout, blacklist y respuesta HTTP 401 al reutilizar el token.

### Benchmark

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
-File .\scripts\benchmark-cache.ps1
```

Resultados almacenados en:

```text
scripts/resultados/benchmark-cache.csv
scripts/resultados/benchmark-resumen.json
```

## Resultados del benchmark

Se realizaron cinco calentamientos y diez repeticiones por escenario.

| Escenario | Promedio | P95 |
|---|---:|---:|
| Sin caché | 29.737 ms | 31.271 ms |
| Con caché | 22.961 ms | 28.383 ms |

Speedup:

```text
1.295x
```

## Pruebas automatizadas

Windows:

```powershell
cd backend
.\mvnw.cmd clean verify
```

Linux o macOS:

```bash
cd backend
./mvnw clean verify
```

Resultado actual:

```text
Tests run: 53
Failures: 0
Errors: 0
BUILD SUCCESS
```

## Cobertura JaCoCo

Reporte:

```text
backend/target/site/jacoco/index.html
```

Resultados:

```text
Cobertura de líneas: 99.42 %
Cobertura de ramas: 73.08 %
Cobertura de instrucciones: 96 %
```

## Integración continua

El workflow se encuentra en:

```text
.github/workflows/ci.yml
```

GitHub Actions ejecuta automáticamente la compilación y las pruebas del backend. El workflow actual se encuentra aprobado en la rama `main`.

## Documentación

La carpeta `docs/` contiene:

- ADR de arquitectura, ORM y Redis.
- Diagramas C4 de contexto, contenedores y componentes.
- Diagramas de secuencia de caché.
- Benchmark.
- Informe académico.
- Evidencias de ejecución.

## Seguridad

No subir al repositorio:

- `.env`
- contraseñas
- secretos JWT
- tokens
- cookies completas
- `node_modules`
- `target`
- archivos privados de configuración

## Universidad

Proyecto académico desarrollado para la Universidad Técnica Estatal de Quevedo por el Equipo H.
