# Preguntas de análisis: arquitectura y caché

## 1. ¿El speedup obtenido justifica la complejidad operativa de Redis?

*Pendiente de completar con el benchmark real de Jaime (sección 8 del documento de caché y rendimiento).*

El criterio para responder esta pregunta ya está definido: se calcula S = T_sin / T_con a partir de al menos 10 mediciones por escenario, y se compara contra el umbral orientativo de S > 2. Si el speedup medido supera ese umbral, la mejora de rendimiento justifica mantener Redis como pieza de infraestructura adicional; si se queda cerca de S = 1, la complejidad operativa de operar Redis (ver pregunta 2) probablemente no se justifica para el volumen de datos y tráfico real de este PFC, que es modesto por tratarse de un proyecto académico.

## 2. ¿Qué costos, riesgos y fallos introduce Redis?

**Costos:** una pieza de infraestructura adicional que el equipo debe desplegar, configurar y mantener disponible (un contenedor más en Docker Compose), y tiempo de aprendizaje para el equipo sobre cómo diagnosticar problemas de caché.

**Riesgos:**
- Ventana breve de inconsistencia entre una escritura y la invalidación efectiva de la caché (ver ADR-003).
- Riesgo de cache stampede si un endpoint muy consultado expira bajo carga concurrente (ver pregunta 4).
- Si Redis no está disponible y el filtro de seguridad depende de él para consultar la blacklist de JTI, un fallo de Redis podría bloquear el flujo de autenticación completo si no se implementa una degradación controlada.

**Fallos posibles:** caída del contenedor de Redis, pérdida de datos en memoria tras un reinicio (a menos que se configure persistencia), y claves obsoletas si `@CacheEvict` no cubre todos los casos de escritura reales.

## 3. ¿Qué claves se invalidan al crear, actualizar o eliminar?

*Pendiente de confirmar con Jaime (ver ADR-003): falta definir si se usa `allEntries = true` sobre el caché completo, o invalidación por clave específica según los parámetros de cada consulta afectada.*

Lo que sí está definido conceptualmente: cualquier operación de escritura (crear, actualizar, eliminar) sobre una entidad debe disparar `@CacheEvict` sobre las entradas de caché que dependan de esa entidad, para evitar que un listado paginado siga mostrando datos obsoletos después de la escritura.

## 4. ¿Existe riesgo de cache stampede y cómo se mitigaría?

Sí existe el riesgo, en cualquier endpoint de lectura frecuente cuya entrada de caché expire mientras hay muchas peticiones concurrentes esperando ese mismo dato (ver sección 5 del documento de caché y rendimiento). Para este PFC, dado su volumen de tráfico académico, el riesgo es bajo en la práctica, pero la mitigación teórica aplicable sería: un mutex que permita que solo una petición recalcule el valor mientras las demás esperan o reciben la versión anterior, o variar aleatoriamente el TTL (jitter) para que las entradas no expiren todas en el mismo instante.

## 5. ¿Cómo se compara el ADR-001 con un sistema web de producción documentado?

Spring PetClinic, la aplicación de referencia oficial mantenida por el propio equipo de Spring, sigue el mismo patrón que ADR-001: arquitectura por capas con Controllers que manejan las peticiones HTTP, Repositories de Spring Data JPA para el acceso a datos, y entidades JPA para el modelo de dominio persistente. Incluso usa `@Cacheable` para cachear una de sus consultas más frecuentes, igual que el patrón cache-aside adoptado en este PFC. La similitud confirma que la decisión de ADR-001 no es una simplificación arbitraria, sino que sigue el mismo patrón que el propio equipo de Spring recomienda como referencia para aplicaciones de este tamaño y complejidad.

## 6. ¿Qué aspectos podrían requerir otra arquitectura si el PFC crece?

Si el volumen de usuarios o el tamaño del equipo de desarrollo creciera significativamente más allá del alcance académico actual, varios aspectos de ADR-001 tendrían que revisarse: la necesidad de escalar módulos de forma independiente favorecería considerar microservicios (descartados en ADR-001 precisamente por no ser necesarios a esta escala); un volumen de escritura mucho mayor podría requerir replantear cache-aside por un patrón write-through; y si aparecieran flujos verdaderamente asíncronos o múltiples consumidores de un mismo evento, la arquitectura orientada a eventos (EDA), descartada en la sección 4 del documento de patrones de arquitectura, volvería a ser una alternativa válida a evaluar.
