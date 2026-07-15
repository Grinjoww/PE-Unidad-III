# Patrones de arquitectura para aplicaciones web escalables

**Equipo:** Equipo H
**Proyecto:** Java 21 + Spring Boot 3.x + Angular 17+ + PostgreSQL 16 + Redis

## 1. Arquitectura de software y su impacto en el proyecto

La arquitectura de software se define como el conjunto de decisiones estructurales fundamentales sobre cómo se organiza un sistema: qué elementos lo componen, cómo se relacionan entre sí y qué principios guían su diseño y evolución (IEEE Computer Society, 2024). No se trata únicamente de un diagrama, sino de las decisiones que determinan atributos de calidad como el rendimiento, la seguridad, la mantenibilidad y la capacidad de escalar. Estas decisiones son difíciles de revertir una vez tomadas, por lo que un error arquitectónico temprano —por ejemplo, acoplar excesivamente la lógica de negocio con el acceso a datos— puede volverse costoso de corregir a medida que el sistema crece. Para un proyecto académico como este PFC, la arquitectura elegida condiciona tanto la velocidad de desarrollo del equipo como la facilidad con la que se pueden incorporar nuevas funcionalidades sin romper las existentes.

## 2. Monolito, monolito modular y arquitectura por capas

Un monolito tradicional concentra toda la lógica de la aplicación en una sola unidad desplegable sin separación clara de responsabilidades, lo que facilita el desarrollo inicial pero dificulta el mantenimiento a largo plazo. Un monolito modular conserva el despliegue único, pero organiza internamente el código en módulos con fronteras bien definidas, reduciendo el acoplamiento interno sin pagar el costo operativo de una arquitectura distribuida (Fowler, 2015). La arquitectura por capas es una forma concreta de lograr esa modularidad: separa el sistema en capas horizontales —presentación, lógica de negocio, acceso a datos— donde cada capa solo se comunica con la inmediata inferior.

En el caso del PFC, las responsabilidades quedan así:

- **Controller:** recibe las solicitudes HTTP, valida el formato de entrada y delega el trabajo a la capa de servicio, sin contener lógica de negocio.
- **Service:** concentra las reglas de negocio, coordina transacciones y decide cuándo consultar caché o base de datos.
- **Repository:** encapsula el acceso a datos mediante Spring Data JPA, aislando al resto del sistema de los detalles de PostgreSQL.
- **Entity:** representa el modelo de dominio persistente, mapeado directamente a las tablas de la base de datos.

**Ventajas de la arquitectura por capas:** separación clara de responsabilidades, curva de aprendizaje baja para el equipo, pruebas más simples por capa y despliegue único que simplifica la infraestructura. **Desventajas:** todas las capas comparten el mismo ciclo de despliegue, el escalado es de la aplicación completa y no de partes específicas, y un cambio transversal puede requerir tocar varias capas a la vez.

## 3. Microservicios frente a monolito modular

Los microservicios dividen el sistema en servicios independientes, cada uno con su propia base de datos y ciclo de despliegue, comunicados por red (Purohit, 2024). Esto permite escalar cada servicio por separado y que equipos distintos trabajen sin bloquearse mutuamente, pero introduce complejidad operativa considerable: hay que gestionar comunicación entre servicios, consistencia de datos distribuidos, observabilidad distribuida y tolerancia a fallos de red que en un monolito simplemente no existen.

Comparando ambos enfoques en las dimensiones relevantes:

- **Acoplamiento:** el monolito modular acopla en tiempo de compilación dentro de un mismo proceso; los microservicios acoplan en tiempo de ejecución vía red.
- **Despliegue:** el monolito se despliega como una unidad; los microservicios se despliegan de forma independiente, lo que exige orquestación (Docker Compose, Kubernetes).
- **Escalado independiente:** solo los microservicios permiten escalar un componente puntual sin replicar todo el sistema.
- **Observabilidad:** un monolito se monitorea con logs y métricas centralizadas; los microservicios requieren trazabilidad distribuida (trazas correlacionadas entre servicios).
- **Tolerancia a fallos:** en el monolito, un fallo interno puede tumbar toda la aplicación; en microservicios, un fallo puede aislarse a un servicio, pero se introducen nuevos puntos de falla en la red.
- **Complejidad operativa:** los microservicios multiplican la cantidad de piezas de infraestructura a mantener (service discovery, balanceadores, mensajería), algo que un equipo pequeño no puede sostener con la misma calidad.

Para un equipo universitario de tres integrantes con tiempo y experiencia operativa limitados, el monolito modular es la opción razonable: concentra el esfuerzo en la lógica del dominio en lugar de en infraestructura distribuida, y evita el llamado *team cognitive load*, es decir, la carga mental que un equipo pequeño asume al tener que entender y operar múltiples servicios, contratos de red y fallos parciales simultáneamente. Este mismo motivo respalda evitar las falacias de los sistemas distribuidos —como asumir que la red siempre es confiable, la latencia es cero o el ancho de banda es infinito—, supuestos que un monolito modular simplemente no necesita afrontar (Thampi, 2009).

## 4. Arquitectura orientada a eventos (EDA)

La arquitectura orientada a eventos organiza el sistema alrededor de productores, que generan eventos cuando ocurre un cambio de estado relevante, y consumidores, que reaccionan a esos eventos de forma asíncrona a través de un event bus o broker que desacopla a ambas partes en el tiempo y en el espacio. Herramientas como RabbitMQ y Apache Kafka implementan este broker para mensajería asíncrona persistente, mientras que WebSockets permiten comunicación bidireccional en tiempo real entre cliente y servidor; estas tecnologías se mencionan aquí como referencia conceptual del patrón y no como componentes que este PFC implemente.

Frente al modelo REST síncrono, donde el cliente espera una respuesta inmediata y ambas partes deben estar disponibles al mismo tiempo, los eventos asíncronos permiten que productor y consumidor operen de forma independiente, mejorando la resiliencia ante picos de carga a costa de mayor complejidad para razonar sobre el orden y la consistencia de los datos. La arquitectura orientada a eventos se justifica cuando existen múltiples consumidores independientes de un mismo evento, procesamiento en segundo plano desacoplado del flujo principal, o necesidad de amortiguar picos de tráfico. El PFC no presenta ninguno de estos escenarios: es un CRUD con autenticación y caché, con un flujo de solicitud-respuesta simple, por lo que introducir Kafka o microservicios agregaría complejidad operativa sin un beneficio real para el alcance del proyecto.

## 5. Modelo C4

El modelo C4, propuesto por Simon Brown (Brown, n.d.), documenta la arquitectura de software en cuatro niveles de abstracción progresiva: el nivel 1 (Contexto) muestra el sistema como una caja negra y sus actores externos; el nivel 2 (Contenedores) descompone el sistema en las aplicaciones y almacenes de datos desplegables que lo componen (frontend, backend, base de datos, caché); el nivel 3 (Componentes) detalla los módulos internos de un contenedor específico y sus responsabilidades; y el nivel 4 (Código) desciende al detalle de clases, aplicable solo cuando se requiere documentación exhaustiva de implementación. Este PFC utiliza los tres primeros niveles, suficientes para comunicar la arquitectura sin caer en el detalle de código que cambia con frecuencia.

## 6. ADR: documentar el razonamiento, no solo el resultado

Un Architectural Decision Record (ADR) es un documento breve que registra una decisión arquitectónica junto con el contexto que la motivó, las alternativas consideradas y las consecuencias esperadas. Su valor no está en el resultado final —que podría inferirse leyendo el código— sino en preservar el razonamiento: por qué se descartaron otras opciones y qué condiciones podrían hacer que esa decisión deba revisarse en el futuro. Sin esta traza, un equipo nuevo o el mismo equipo meses después pierde el contexto que explica por qué el sistema está construido como está.

## Referencias

Fowler, M. (2015). *Microservices: a definition of this new architectural term*. martinfowler.com. https://martinfowler.com/articles/microservices.html

IEEE Computer Society. (2024). *Guide to the software engineering body of knowledge (SWEBOK), versión 4.0*. https://ieeecs-media.computer.org/media/education/swebok/swebok-v4.pdf

Purohit, T. (2024). Microservices vs. monolithic architectures: A comparative analysis. *International Journal of Advanced Research in Computer Science & Technology, 7*(4), 10600–10603. https://doi.org/10.15662/IJARCST.2024.0704001

Thampi, S. M. (2009). *Introduction to distributed systems*. arXiv. https://arxiv.org/abs/0911.4395

Brown, S. (n.d.). *The C4 model for visualising software architecture*. c4model.com. https://c4model.com
