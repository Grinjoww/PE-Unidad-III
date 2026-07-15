# Caché, Redis y medición de rendimiento

## 1. Qué es una caché y en qué se diferencia del almacenamiento persistente

Una caché es una capa de almacenamiento temporal que guarda copias de datos de acceso frecuente más cerca de donde se necesitan, con el objetivo de reducir la latencia y aliviar la carga sobre el almacén de datos principal. A diferencia del almacenamiento persistente, una caché es volátil y está optimizada para velocidad, no para durabilidad: si se pierde, el sistema puede seguir funcionando consultando directamente la fuente de datos original, solo que más lento. En este PFC, PostgreSQL es la fuente de verdad y Redis actúa como un acelerador desechable sobre esa fuente.

## 2. Niveles de caché

- **Caché del navegador:** almacena assets estáticos (HTML, CSS, JS, imágenes) directamente en el dispositivo del usuario. El TTL se controla con cabeceras HTTP como Cache-Control, típicamente de horas a días. La invalidación se logra versionando los nombres de archivo o usando ETags, no eliminando manualmente el contenido.
- **CDN:** distribuye contenido estático o semi-estático en servidores geográficamente cercanos al usuario. TTL de minutos a horas. La invalidación requiere una purga explícita en el proveedor del CDN.
- **Caché de aplicación (Redis, en este PFC):** guarda resultados de consultas o cómputos específicos del backend. TTL de segundos a minutos, ajustado a cuánta obsolescencia tolera cada endpoint. La invalidación es explícita, disparada por el propio código cuando los datos subyacentes cambian.
- **Caché de base de datos:** interna al motor (por ejemplo, el buffer pool de PostgreSQL), gestionada automáticamente por el propio motor y no configurable directamente desde la aplicación.

## 3. Patrones de acceso a caché

El patrón cache-aside (o lazy loading) hace que la aplicación consulte primero la caché; si no encuentra el dato (cache miss), lo busca en la base de datos, lo guarda en caché y lo retorna (Microsoft, n.d.). Su ventaja principal es la simplicidad y que la caché solo contiene lo que realmente se ha solicitado; su desventaja es que la primera petición siempre paga el costo completo de la consulta. El patrón read-through delega esa lógica de "buscar y poblar" a la propia librería de caché, de forma transparente para la aplicación. El patrón write-through escribe simultáneamente en la caché y en la base de datos, garantizando que la caché nunca esté desactualizada, a costa de mayor latencia en cada escritura (Microsoft, n.d.). El patrón write-behind (o write-back) escribe primero en caché y traslada el cambio a la base de datos de forma asíncrona y por lotes, lo que ofrece la menor latencia de escritura, pero introduce riesgo de pérdida de datos si la caché falla antes de sincronizar.

Este PFC utilizará cache-aside porque encaja de forma natural con su naturaleza de CRUD sobre PostgreSQL: la base de datos sigue siendo la fuente de verdad, y Spring Data ya implementa esta semántica de forma nativa a través de las anotaciones `@Cacheable` y `@CacheEvict` (Spring Framework, n.d.). Además, tolera perfectamente la breve obsolescencia que implica un endpoint de listado paginado, que es el caso de uso principal de caché en este proyecto.

## 4. Cache hit, cache miss, TTL e invalidación

Un cache hit ocurre cuando el dato solicitado se encuentra en la caché y se sirve sin tocar la base de datos. Un cache miss ocurre cuando no está presente o ya expiró, obligando a consultar la base de datos y repoblar la caché. El TTL (time to live) define cuánto tiempo permanece válida una entrada antes de expirar automáticamente, equilibrando frescura de los datos contra carga sobre la base de datos. La invalidación, en cambio, es la eliminación explícita de una entrada cuando se sabe con certeza que quedó desactualizada (por ejemplo, tras un update), sin esperar a que el TTL expire por sí solo.

## 5. Cache stampede y mitigaciones

Un cache stampede (o thundering herd) ocurre cuando una entrada de caché muy solicitada expira y, en el mismo instante, cientos o miles de peticiones concurrentes fallan a la vez y golpean simultáneamente la base de datos para recalcular el mismo valor. Facebook documentó este problema a gran escala: al introducir "cache leases" para mitigar el stampede en su infraestructura de memcache, lograron reducir el pico de consultas a la base de datos de 17 000 a 1 300 consultas por segundo durante un evento de stampede, una reducción de aproximadamente 13 veces (Nishtala et al., 2013). Las mitigaciones más comunes son: un mutex o lock que permite que solo una petición recalcule el valor mientras las demás esperan o reciben la versión anterior; la variación aleatoria del TTL (jitter), que evita que muchas entradas expiren exactamente al mismo tiempo; y la expiración anticipada probabilística, donde una pequeña fracción de las peticiones refresca el valor un poco antes de que expire realmente, repartiendo el costo de recalcularlo en vez de concentrarlo en un solo instante.

## 6. Redis y sus estructuras de datos

Redis es un almacén de estructuras de datos en memoria (Redis, n.d.) que ofrece varios tipos más allá del simple par clave-valor: strings (el tipo más básico, usado para contadores, tokens de sesión o valores simples), hashes (mapas de campo-valor, ideales para representar objetos como un usuario sin ocupar mucho espacio), lists (colecciones ordenadas por inserción, útiles como colas), sets (colecciones no ordenadas de elementos únicos, para pruebas de pertenencia), sorted sets (similares a los sets pero con cada elemento asociado a un puntaje que define su orden, útiles en rankings o limitadores de tasa) y streams (una estructura de registro append-only, pensada para procesar eventos en orden). Este PFC solo utiliza strings con expiración, tanto para el caché de consultas como para la blacklist de tokens.

La diferencia esencial con PostgreSQL es que Redis vive en memoria y prioriza velocidad sobre durabilidad y capacidad de consulta compleja, mientras que PostgreSQL es un motor relacional en disco, con garantías ACID, joins y persistencia como prioridad de diseño. En este PFC, PostgreSQL sigue siendo la fuente de verdad; Redis es un acelerador volátil que puede perderse sin comprometer la integridad de los datos.

## 7. Los dos usos de Redis en este PFC

El primer uso es caché de consultas mediante el patrón cache-aside: los endpoints de lectura frecuente se anotan con `@Cacheable`, usando una clave compuesta por los parámetros relevantes (página, tamaño, filtros) y un TTL acorde a cuánta obsolescencia tolera ese listado; cuando ocurre una escritura sobre esos datos, `@CacheEvict` elimina las entradas afectadas para evitar servir información desactualizada.

El segundo uso es la blacklist de JTI para el logout de JWT: al cerrar sesión, el backend extrae el identificador único del token (JTI) y lo guarda como clave en Redis con un TTL igual al tiempo de vida restante del token. El filtro de seguridad consulta esa clave en cada petición; si existe, el token se considera revocado y la petición se rechaza con 401, aunque el JWT en sí siga siendo válido según su firma y fecha de expiración. Usar TTL en Redis para esto es conveniente porque la entrada se autoelimina cuando el token habría expirado de todas formas, sin necesidad de un proceso de limpieza manual.

## 8. Metodología de medición de rendimiento

Para medir el impacto real de la caché, las mediciones deben hacerse en condiciones controladas: la misma consulta, en la misma máquina, con la caché ya "caliente" (excluyendo la primera petición de calentamiento, que siempre sería un cache miss), y con un mínimo de 10 repeticiones por escenario (con caché y sin caché) para reducir el efecto del ruido y la variabilidad puntual.

De esas repeticiones se calcula el promedio (la media aritmética de los tiempos medidos) y el P95 (el valor por debajo del cual cae el 95 % de las mediciones), que es más representativo que el promedio porque refleja el peor caso realista sin dejarse arrastrar por un único valor extremo. El speedup se calcula como S = T_sin / T_con, donde T_sin es el tiempo promedio sin caché y T_con es el tiempo promedio con caché. Un valor de S > 1 indica que la caché sí mejora el rendimiento; un umbral orientativo de S > 2 (es decir, al menos reducir el tiempo a la mitad) suele considerarse el punto en el que la mejora justifica la complejidad operativa adicional de mantener Redis en producción.

## Referencias

Microsoft. (s.f.). *Cache-Aside pattern*. Azure Architecture Center. https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside

Nishtala, R., Fugal, H., Grimm, S., Kwiatkowski, M., Lee, H., Li, H. C., McElroy, R., Paleczny, M., Peek, D., Saab, P., Stafford, D., Tung, T., & Venkataramani, V. (2013). Scaling Memcache at Facebook. *Proceedings of the 10th USENIX Symposium on Networked Systems Design and Implementation (NSDI '13)*, 385–398. https://www.usenix.org/system/files/conference/nsdi13/nsdi13-final170_update.pdf

Redis. (s.f.). *Understand Redis data types*. https://redis.io/docs/latest/develop/data-types/

Spring Framework. (s.f.). *Cache Abstraction*. https://docs.spring.io/spring-framework/reference/integration/cache.html
