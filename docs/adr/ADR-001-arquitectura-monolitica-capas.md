# ADR-001: Arquitectura monolítica modular por capas

## Estado

Aceptada

## Contexto

El PFC (Proyecto de Fin de Carrera) es un sistema CRUD con autenticación y caché, desarrollado por un equipo universitario de tres integrantes con tiempo y experiencia operativa limitados, dentro del stack tecnológico definido: Java 21, Spring Boot 3.x, Angular 17+, PostgreSQL 16 y Redis 7. Era necesario decidir la arquitectura general del sistema antes de comenzar la implementación, considerando tanto la velocidad de desarrollo del equipo como la mantenibilidad del código a lo largo del semestre.

## Decisión

Se adopta un monolito modular con arquitectura por capas:
Angular 17+ → API REST Spring Boot 3 → Controller → Service → Repository → Entity → PostgreSQL 16
Redis 7 se incorpora como una capa adicional, usada exclusivamente para caché de consultas (patrón cache-aside) y para la blacklist temporal de JTI en el logout de JWT.

## Consecuencias

**Positivas:**
- Un único artefacto desplegable simplifica la infraestructura, algo relevante dado el tiempo acotado del semestre.
- La separación en capas (Controller, Service, Repository, Entity) facilita las pruebas por capa y reduce el acoplamiento interno.
- La curva de aprendizaje es baja, ajustada a la experiencia operativa real del equipo.
- Se evita el team cognitive load y las falacias de los sistemas distribuidos (asumir red confiable, latencia cero o ancho de banda infinito) que sí tendría que afrontar una arquitectura distribuida.

**Negativas:**
- Todas las capas comparten el mismo ciclo de despliegue: no es posible escalar una capa de forma independiente.
- Un fallo interno grave puede afectar la aplicación completa, a diferencia de un sistema con aislamiento de fallos por servicio.
- Un cambio transversal (por ejemplo, agregar un campo que atraviese varias capas) puede requerir tocar Controller, Service, Repository y Entity a la vez.

## Alternativas consideradas

**Microservicios.** Descartada por la complejidad operativa que introduce (comunicación entre servicios, consistencia de datos distribuidos, observabilidad distribuida, orquestación con Docker Compose o Kubernetes), desproporcionada frente al alcance real de un proyecto académico de tres personas.

**Arquitectura hexagonal completa (puertos y adaptadores).** Descartada porque agrega una capa adicional de abstracción (puertos y adaptadores) que no se justifica para el alcance de un CRUD académico; la arquitectura por capas ya logra la separación de responsabilidades necesaria con menor complejidad para el nivel de experiencia del equipo.

**Aplicación monolítica sin separación por capas.** Descartada porque concentraría toda la lógica en una unidad sin fronteras claras, dificultando el mantenimiento y las pruebas a medida que el proyecto crece, incluso dentro de un plazo académico corto.

**Arquitectura orientada a eventos (EDA) como eje principal.** Descartada porque el PFC no presenta los escenarios que justifican EDA: no hay múltiples consumidores independientes de un mismo evento, ni procesamiento en segundo plano desacoplado, ni necesidad de amortiguar picos de tráfico. Introducir un broker de eventos agregaría complejidad operativa sin un beneficio real para el alcance del proyecto.
