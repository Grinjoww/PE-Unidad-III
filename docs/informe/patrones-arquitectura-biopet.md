Patrones de arquitectura web

La arquitectura de software determina cómo se organiza un sistema y qué tan fácil resulta mantenerlo y hacerlo crecer con el tiempo [1]. Existen varios estilos arquitectónicos para aplicaciones web, cada uno con compromisos distintos entre simplicidad, escalabilidad y complejidad operativa.

El monolito por capas organiza el sistema en niveles horizontales —presentación, lógica de negocio, acceso a datos— donde cada capa se comunica solo con la inmediata inferior [2]. Este estilo favorece la reutilización, ya que distintas implementaciones de una capa pueden intercambiarse si respetan la misma interfaz hacia las capas vecinas, y facilita el mantenimiento al dividir el sistema en niveles de responsabilidad bien delimitados [2]. BIOPET, el sistema de gestión veterinaria desarrollado en este proyecto, adopta exactamente este patrón: Angular 17.3 como frontend, Spring Boot 3.2.12 con Java 21 como backend organizado en Controller, Service y Repository, y PostgreSQL 16 como base de datos, según lo documentado en ADR-001. Esta decisión se justifica por el tamaño reducido del equipo (tres integrantes) y el plazo académico disponible, que no permiten sostener la complejidad operativa de arquitecturas más distribuidas.

Los microservicios dividen el sistema en servicios pequeños e independientes, cada uno con su propia base de datos y ciclo de despliegue, comunicados por red [3]. Esto permite escalar cada servicio por separado y que distintos equipos trabajen sin bloquearse, pero introduce complejidad operativa considerable: comunicación entre servicios, consistencia de datos distribuidos y tolerancia a fallos de red que un monolito simplemente no enfrenta [3]. Para el alcance de BIOPET, esta complejidad no se justifica.

La arquitectura dirigida por eventos (event-driven) organiza el sistema alrededor de productores que generan eventos y consumidores que reaccionan de forma asíncrona, desacoplados en el tiempo mediante un intermediario. Este estilo mejora la resiliencia ante picos de carga, pero exige mayor complejidad para razonar sobre el orden de los eventos, y se justifica principalmente cuando existen múltiples consumidores independientes de un mismo evento — un escenario que BIOPET, un CRUD con autenticación y caché, no presenta.

Finalmente, el modelo serverless permite ejecutar código en respuesta a eventos sin aprovisionar ni administrar servidores directamente, delegando el escalado, el parcheo y la disponibilidad al proveedor de la nube [4]. Aunque atractivo por su modelo de pago por uso, este estilo introduce dependencia de un proveedor específico y cambia significativamente el modelo de desarrollo y despliegue, algo que excede el alcance y el plazo de un proyecto académico como BIOPET.

En conjunto, de los cuatro estilos revisados, el monolito por capas es el que mejor equilibra simplicidad de desarrollo, facilidad de prueba y mantenibilidad para un proyecto del tamaño y plazo de BIOPET, motivo por el cual fue la decisión adoptada en ADR-001.

Referencias
[1] IEEE Computer Society, "Guide to the Software Engineering Body of Knowledge (SWEBOK), versión 4.0," 2024. [Online]. Disponible: https://ieeecs-media.computer.org/media/education/swebok/swebok-v4.pdf
[2] D. Garlan and M. Shaw, "An Introduction to Software Architecture," 1994. [Online]. Disponible: http://sunnyday.mit.edu/16.355/intro_softarch.pdf
[3] M. Fowler, "Microservices: a definition of this new architectural term," 2015. [Online]. Disponible: https://martinfowler.com/articles/microservices.html
[4] Amazon Web Services, "What is Serverless Computing?" [Online]. Disponible: https://aws.amazon.com/what-is/serverless-computing/
