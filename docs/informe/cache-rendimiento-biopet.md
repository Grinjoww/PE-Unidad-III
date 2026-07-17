Caché y rendimiento

La caché es una capa de almacenamiento temporal que reduce la latencia y la carga sobre la base de datos al guardar copias de datos de acceso frecuente más cerca de donde se necesitan. Existen varios patrones para mantener sincronizada una caché con su fuente de datos, y BIOPET implementa solo uno de ellos de forma real.

El patrón cache-aside hace que la aplicación consulte primero la caché; si el dato no está (cache miss), consulta la base de datos y guarda el resultado en caché para peticiones futuras [5]. BIOPET implementa este patrón sobre el endpoint GET /api/mascotas, usando Redis 7 como almacén en memoria [6]. La caché se llama mascotas-listado, y su clave se compone de página, tamaño, ordenamiento y filtros de nombre, especie y raza, de modo que cada combinación genera una entrada distinta en Redis. El TTL configurado es de 300 segundos (5 minutos), tras los cuales cada entrada expira automáticamente.

El patrón read-through delega la lógica de "buscar y poblar" a la propia librería de caché, de forma transparente para la aplicación, a diferencia de cache-aside, donde es la aplicación la que controla explícitamente ese flujo. El patrón write-through escribe simultáneamente en la caché y en la base de datos, garantizando que la caché nunca esté desactualizada a costa de mayor latencia en cada escritura. El patrón write-behind escribe primero en caché y traslada el cambio a la base de datos de forma asíncrona, ofreciendo la menor latencia de escritura posible pero con riesgo de pérdida de datos si la caché falla antes de sincronizar. Ninguno de estos tres patrones está implementado en BIOPET; el sistema solo usa cache-aside para lectura, e invalidación explícita para escritura.

En BIOPET, la invalidación se realiza con @CacheEvict(allEntries=true) al crear, actualizar o eliminar una mascota, eliminando todas las entradas de la caché mascotas-listado de una sola vez, en vez de una clave puntual, porque una misma mascota puede aparecer en múltiples combinaciones de página, tamaño, orden y filtros simultáneamente.

Para verificar el impacto real de esta caché, se midieron 10 repeticiones sin caché y 10 con caché, bajo condiciones controladas y tras cinco peticiones de calentamiento. El promedio sin caché fue de 29.737 ms (P95: 31.271 ms), frente a 22.961 ms con caché (P95: 28.383 ms), lo que arroja un speedup de 1.295x. Este resultado confirma una mejora medible, aunque moderada, de rendimiento con la caché habilitada, en las condiciones específicas de la prueba.

Referencias
[5] Microsoft, "Cache-Aside pattern," Azure Architecture Center. [Online]. Disponible: https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside
[6] Redis, "Understand Redis data types." [Online]. Disponible: https://redis.io/docs/latest/develop/data-types/
