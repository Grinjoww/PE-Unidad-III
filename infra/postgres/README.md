# infra/postgres

Carpeta montada en `/docker-entrypoint-initdb.d` del contenedor `postgres` en `docker-compose.yml`.

Actualmente vacía: el esquema se gestiona con Flyway (`backend/src/main/resources/db/migration`), no con scripts de inicialización de Postgres. Si en el futuro se necesita un script de arranque (extensiones, roles adicionales, etc.), colocarlo aquí como `.sql` o `.sh`.
