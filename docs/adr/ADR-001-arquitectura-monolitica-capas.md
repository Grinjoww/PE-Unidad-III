# ADR-001: Arquitectura monolítica por capas — BIOPET

## Estado
Aceptada

## Contexto
BIOPET es un sistema de gestión veterinaria desarrollado por un equipo universitario de tres integrantes, con autenticación de usuarios, un módulo CRUD de mascotas, y caché de consultas. El stack tecnológico real es Angular 17.3, Spring Boot 3.2.12 con Java 21, PostgreSQL 16 y Redis 7. Era necesario decidir la arquitectura general del sistema antes de implementarlo, priorizando la velocidad de desarrollo del equipo y la mantenibilidad del código dentro del plazo académico disponible.

## Decisión
Se adopta un monolito por capas:
Angular 17.3 (puerto 4200) → API REST Spring Boot 3.2.12 / Java 21 (puerto 8080) → Controller → Service → Repository → Entidad JPA → PostgreSQL 16 (puerto 5432, Docker)
Redis 7 (puerto 6379) se incorpora como una capa adicional, usada para caché de consultas (cache-aside) y blacklist de JWT.

## Consecuencias

**Positivas:**
- Un único artefacto desplegable simplifica la infraestructura, adecuado para el tiempo y experiencia operativa del equipo.
- La separación en capas facilita las pruebas: el proyecto alcanzó 53 pruebas aprobadas con 99.42 % de cobertura de líneas y 73.08 % de cobertura de ramas (JaCoCo).
- Puertos y responsabilidades claramente delimitados (4200, 8080, 5432, 6379) facilitan el diagnóstico de problemas por capa.

**Negativas (limitación real observada):**
- Todas las capas comparten el mismo ciclo de despliegue: no es posible escalar el backend de forma independiente del resto.
- La dependencia de Redis para caché y blacklist introduce un punto único de fallo adicional: actualmente no existe un `CacheErrorHandler` personalizado, por lo que si Redis no está disponible, las operaciones que dependen de caché o de la blacklist de JWT pueden fallar en vez de degradarse consultando PostgreSQL directamente.

## Alternativas consideradas
**Microservicios.** Descartada por la complejidad operativa desproporcionada frente al alcance de un proyecto académico de tres integrantes.

**Aplicación monolítica sin separación por capas.** Descartada porque dificultaría el mantenimiento y las pruebas automatizadas a medida que el proyecto creciera.

**Arquitectura orientada a eventos como eje principal.** Descartada porque BIOPET es un CRUD con autenticación y caché, sin múltiples consumidores independientes de un mismo evento ni necesidad de amortiguar picos de tráfico.
