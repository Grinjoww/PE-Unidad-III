# infra/redis

Configuración de infraestructura para Redis 7 usado como caché (patrón cache-aside, pendiente de implementación) y como almacén de blacklist de JTI para JWT.

Actualmente se usa la imagen oficial `redis:7-alpine` sin configuración adicional (ver `docker-compose.yml`). Si se necesita un `redis.conf` personalizado, colocarlo aquí y montarlo en el servicio `redis`.
