# Preguntas de análisis: arquitectura y caché

## 1. ¿El speedup obtenido justifica la complejidad operativa de Redis?

El benchmark real (10 repeticiones, 5 de calentamiento; ver docs/informe/benchmark-biopet.md) dio un promedio de 29.737 ms sin caché frente a 22.961 ms con caché, es decir S = T_sin / T_con = 1.295x. Este valor queda por debajo del umbral orientativo de S > 2, por lo que, medido de forma estrictamente cuantitativa, la caché no alcanza el punto en el que la mejora de rendimiento justifica por sí sola la complejidad operativa de mantener Redis.

Esto es coherente con lo esperado dado el alcance del proyecto: el dataset de prueba tiene solo 58 mascotas y la consulta cacheada es una sola paginación sin joins costosos, por lo que PostgreSQL ya resuelve la consulta sin caché en muy pocos milisegundos — hay poco margen de mejora posible. En un entorno de producción con un volumen de datos y de tráfico concurrente mucho mayor (el escenario de 10 000 usuarios concurrentes descrito en la sección de escalabilidad horizontal), la brecha entre consultar PostgreSQL directamente y servir desde Redis se ampliaría, y el speedup esperado sería considerablemente mayor a 1.295x. Para este PFC académico, Redis se mantiene principalmente porque también resuelve la blacklist de JWT (un requisito funcional, no solo de rendimiento) y porque demuestra el patrón cache-aside correctamente implementado; el speedup modesto es un resultado honesto del volumen de datos de prueba, no un defecto de la implementación.

## 2. ¿Qué costos, riesgos y fallos introduce Redis?

**Costos:** una pieza de infraestructura adicional que el equipo debe desplegar, configurar y mantener disponible (un contenedor más en Docker Compose), y tiempo de aprendizaje para el equipo sobre cómo diagnosticar problemas de caché.

**Riesgos:**
- Ventana breve de inconsistencia entre una escritura y la invalidación efectiva de la caché (ver ADR-003).
- Riesgo de cache stampede si un endpoint muy consultado expira bajo carga concurrente (ver pregunta 4).
- Si Redis no está disponible y el filtro de seguridad depende de él para consultar la blacklist de JTI, un fallo de Redis podría bloquear el flujo de autenticación completo si no se implementa una degradación controlada.

**Fallos posibles:** caída del contenedor de Redis, pérdida de datos en memoria tras un reinicio (a menos que se configure persistencia), y claves obsoletas si `@CacheEvict` no cubre todos los casos de escritura reales.

## 3. ¿Qué claves se invalidan al crear, actualizar o eliminar?

Confirmado en la implementación (MascotaService.crear/actualizar/eliminar, ver ADR-003): se usa @CacheEvict(cacheNames = CacheConfig.CACHE_MASCOTAS_LISTADO, allEntries = true). Es decir, no se invalida una clave puntual — se borran de golpe todas las entradas del caché mascotas-listado, sin importar qué combinación de página, tamaño, orden o filtros tenían.

Se eligió allEntries = true en vez de invalidación por clave específica porque una misma mascota puede aparecer simultáneamente en múltiples combinaciones de página/tamaño/orden/filtros, y enumerar de antemano todas las claves afectadas por un solo cambio sería complejo y propenso a errores (una clave olvidada dejaría datos obsoletos servidos indefinidamente hasta que expire el TTL de 300 s). El costo de esta decisión es una invalidación más agresiva de lo estrictamente necesario: un cambio en una sola mascota descarta también listados que no la incluían. Para el volumen de datos de este PFC (58 mascotas, un único endpoint cacheado) ese costo es asumible; en un catálogo mucho más grande con muchas más combinaciones de filtros, valdría la pena revisar una estrategia de invalidación más selectiva (por ejemplo, agrupando por especie).
## 4. ¿Existe riesgo de cache stampede y cómo se mitigaría?

Sí existe el riesgo, en cualquier endpoint de lectura frecuente cuya entrada de caché expire mientras hay muchas peticiones concurrentes esperando ese mismo dato (ver sección 5 del documento de caché y rendimiento). Para este PFC, dado su volumen de tráfico académico, el riesgo es bajo en la práctica, pero la mitigación teórica aplicable sería: un mutex que permita que solo una petición recalcule el valor mientras las demás esperan o reciben la versión anterior, o variar aleatoriamente el TTL (jitter) para que las entradas no expiren todas en el mismo instante.

## 5. ¿Cómo se compara el ADR-001 con un sistema web de producción documentado?

Spring PetClinic, la aplicación de referencia oficial mantenida por el propio equipo de Spring, sigue el mismo patrón que ADR-001: arquitectura por capas con Controllers que manejan las peticiones HTTP, Repositories de Spring Data JPA para el acceso a datos, y entidades JPA para el modelo de dominio persistente. Incluso usa `@Cacheable` para cachear una de sus consultas más frecuentes, igual que el patrón cache-aside adoptado en este PFC. La similitud confirma que la decisión de ADR-001 no es una simplificación arbitraria, sino que sigue el mismo patrón que el propio equipo de Spring recomienda como referencia para aplicaciones de este tamaño y complejidad.

## 6. ¿Qué aspectos podrían requerir otra arquitectura si el PFC crece?

Si el volumen de usuarios o el tamaño del equipo de desarrollo creciera significativamente más allá del alcance académico actual, varios aspectos de ADR-001 tendrían que revisarse: la necesidad de escalar módulos de forma independiente favorecería considerar microservicios (descartados en ADR-001 precisamente por no ser necesarios a esta escala); un volumen de escritura mucho mayor podría requerir replantear cache-aside por un patrón write-through; y si aparecieran flujos verdaderamente asíncronos o múltiples consumidores de un mismo evento, la arquitectura orientada a eventos (EDA), descartada en la sección 4 del documento de patrones de arquitectura, volvería a ser una alternativa válida a evaluar.
