# Escalabilidad horizontal

## 1. Objetivo y alcance de este análisis

Este documento describe un **escenario propuesto**: cómo escalaría el PFC si tuviera que soportar hasta 10 000 usuarios concurrentes. No es una arquitectura desplegada ni probada — el despliegue real del proyecto corre en una sola instancia, sin balanceador ni réplicas, según lo definido en ADR-001. El objetivo es demostrar comprensión de los mecanismos de escalado horizontal, no describir el estado actual del sistema.

## 2. Balanceador de carga

NGINX actúa como reverse proxy y balanceador de carga entre las instancias del backend, distribuyendo las peticiones entrantes mediante el método round-robin (el que usa por defecto) o least-connections cuando el tiempo de procesamiento varía entre peticiones (NGINX, s.f.). Se eligió NGINX por ser un estándar de la industria, ligero y ampliamente documentado, adecuado para un equipo con experiencia operativa limitada.

## 3. Múltiples instancias del backend Spring Boot

En este escenario, el backend se replicaría en N instancias idénticas detrás del balanceador. Esto solo es posible porque el backend es stateless: no guarda sesión en memoria, y toda la información de autenticación viaja en el propio JWT. Cualquier instancia puede atender cualquier petición sin necesitar contexto previo de otra instancia.

## 4. Frontend Angular servido por servidor web o CDN

El build de producción de Angular (`ng build`) genera archivos estáticos (HTML, CSS, JS) que no requieren un servidor de aplicación: pueden servirse desde un servidor web ligero o distribuirse mediante una CDN para reducir la latencia geográfica. Esta capa escala de forma independiente al backend, ya que no ejecuta lógica de negocio.

## 5. Redis compartido para caché y blacklist

Un punto crítico en este escenario es que **todas las instancias del backend deben apuntar al mismo Redis compartido**, no a un Redis local por instancia. Si cada instancia tuviera su propio Redis, un token invalidado en la instancia 1 seguiría pareciendo válido en la instancia 2, rompiendo la blacklist de JWT; y la caché de consultas se volvería inconsistente entre instancias. Redis compartido es, por tanto, un requisito de correctitud, no solo de rendimiento, en cualquier escenario con más de una instancia del backend.

## 6. PostgreSQL: primario y réplica de lectura

Se propone un primario que recibe todas las escrituras y una réplica de lectura sincronizada mediante streaming replication, el mecanismo nativo de PostgreSQL para mantener una o más réplicas actualizadas en tiempo casi real a partir del WAL (write-ahead log) del primario (PostgreSQL, s.f.). Las consultas de solo lectura podrían dirigirse a la réplica para aliviar la carga del primario, aceptando que, por ser streaming replication asíncrona, existe un breve margen de retraso entre una escritura en el primario y su visibilidad en la réplica.

## 7. Eliminación de sticky sessions

Como el backend es stateless por diseño (autenticación vía JWT sin sesión en el servidor), este PFC no necesita sticky sessions: cualquier instancia puede validar cualquier petición de forma independiente. Esto sigue directamente el principio de procesos stateless y "share-nothing" de la metodología Twelve-Factor App, que señala explícitamente que depender de sticky sessions es una violación de ese principio y una fuente de problemas de balanceo desigual (Wiggins, 2011).

## 8. Contenedores, observabilidad y puntos únicos de fallo

**Contenedores:** cada componente (frontend, backend, Redis, PostgreSQL) ya corre en un contenedor separado vía Docker Compose en el entorno actual; escalar horizontalmente implicaría pasar a un orquestador con auto-escalado (por ejemplo Kubernetes), algo que este PFC no implementa ni prueba, solo se menciona como paso lógico siguiente.

**Observabilidad:** con una sola instancia, revisar logs locales es suficiente. Con múltiples instancias, se necesitaría centralizar logs y métricas (por ejemplo con una pila de logging agregado) y trazabilidad correlacionada entre instancias para poder diagnosticar un problema que podría estar ocurriendo en cualquiera de ellas. Este PFC no implementa observabilidad distribuida; se reconoce como una brecha del escenario propuesto frente a un sistema de producción real.

**Puntos únicos de fallo (SPOF):**
- El propio NGINX es un punto único de fallo si no se duplica (por ejemplo, con failover activo-pasivo).
- PostgreSQL primario es un punto único de fallo para las escrituras; la réplica solo mitiga la carga de lectura, no reemplaza al primario ante una caída sin un mecanismo adicional de failover.
- Redis, si corre como instancia única, es un punto único de fallo tanto para la caché como para la blacklist de JWT; un despliegue real necesitaría Redis en modo cluster o con Sentinel para alta disponibilidad.

## 9. Escenario propuesto frente al despliegue realmente probado

Todo lo anterior es un escenario teórico para justificar cómo escalaría el sistema, no lo que actualmente corre. El despliegue real y probado de este PFC es: una sola instancia del backend, un solo contenedor de PostgreSQL sin réplica, un solo contenedor de Redis sin clustering, y sin balanceador de carga — tal como se documentó en ADR-001. Ninguno de los componentes descritos en este análisis (múltiples instancias, réplica de lectura, balanceador) ha sido desplegado ni medido en este proyecto.

## Referencias

NGINX. (s.f.). *HTTP Load Balancing*. https://nginx.org/en/docs/http/load_balancing.html

PostgreSQL. (s.f.). *Replication*. https://www.postgresql.org/docs/current/runtime-config-replication.html

Wiggins, A. (2011). *The Twelve-Factor App*. https://12factor.net
