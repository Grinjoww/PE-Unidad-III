# ADR-003: Redis con patrón cache-aside — BIOPET

## Estado
Aceptada

## Contexto
BIOPET necesita reducir la latencia del endpoint de listado de mascotas (`GET /api/mascotas`), el más consultado del sistema, sin reemplazar a PostgreSQL como fuente de verdad. Redis 7 se eligió por ser un almacén en memoria con soporte nativo de expiración automática por TTL, útil tanto para caché de consultas como para la blacklist de JTI del logout de JWT.

## Decisión
Se implementa cache-aside sobre el endpoint de listado, con las siguientes características reales:

- **Nombre de la caché:** `mascotas-listado` (`CacheConfig.CACHE_MASCOTAS_LISTADO`).
- **Clave:** compuesta por página, tamaño, ordenamiento y filtros (nombre, especie, raza), con el formato `mascotas:listado:page=0:size=50:sort=nombre,asc:nombre=:especie=:raza=`. Cada combinación distinta genera una entrada distinta.
- **TTL:** 300 segundos (5 minutos), configurable vía `app.cache.mascotas-listado.ttl-seconds`.
- **Invalidación:** `@CacheEvict(cacheNames="mascotas-listado", allEntries=true)` al crear, actualizar o eliminar una mascota. Se invalidan todas las entradas de golpe, no una clave puntual, porque una misma mascota puede aparecer en múltiples páginas, tamaños, ordenamientos y filtros simultáneamente.

## Consecuencias

**Positivas:**
- Reducción medible de la latencia: benchmark con 10 mediciones por escenario mostró un promedio de 29.737 ms sin caché frente a 22.961 ms con caché (P95 de 31.271 ms a 28.383 ms), un speedup de 1.295x.
- El TTL de 5 minutos limita el tiempo máximo que una entrada puede quedar desactualizada, incluso si la invalidación fallara por algún motivo.

**Negativas (limitación real, no hipotética):**
- No existe un `CacheErrorHandler` personalizado ni mecanismo de degradación controlada. Si Redis no está disponible, tanto las operaciones que dependen de la caché de mascotas como la verificación de tokens en la blacklist de JWT (usada en logout) pueden fallar directamente, en vez de continuar funcionando de forma más lenta consultando PostgreSQL.
- `allEntries=true` es una invalidación más agresiva de lo estrictamente necesario: borra toda la caché de listados aunque el cambio haya afectado a una sola combinación de filtros.

## Alternativas consideradas
**Caché local sin Redis.** Descartada porque no se comparte entre instancias si el backend llegara a escalar horizontalmente.

**No usar blacklist de JWT.** Descartada por motivos de seguridad: un token robado seguiría siendo válido después del logout.

**Invalidación por clave específica en vez de `allEntries=true`.** Evaluada pero descartada por la complejidad de enumerar todas las combinaciones posibles de página, tamaño, orden y filtros afectadas por un solo cambio.

**Tokens de vida muy corta en vez de blacklist.** Descartada porque, aunque reduciría la ventana de riesgo, obligaría a refrescar el token con mucha frecuencia, empeorando la experiencia de usuario sin eliminar el problema de fondo.

**Sesiones completas gestionadas por el servidor (en vez de JWT).** Descartada porque abandona las ventajas de JWT (statelessness, escalabilidad horizontal sin sesiones compartidas) que motivaron su elección original para este PFC.
