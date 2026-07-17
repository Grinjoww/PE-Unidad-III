# ADR: Selección de tecnología ORM para BIOPET (Doctrine, Entity Framework Core, Hibernate/JPA)

> Nota de numeración: el documento de tareas más reciente identifica esta decisión como "ADR-002: selección del ORM". Se guarda en un archivo separado (en vez de sobrescribir `docs/adr/ADR-002-angular-vs-react.md`) para no perder ese ADR ya entregado. Ambos ADR quedan disponibles; quien ensamble el informe final decide cuál numeración usar en el PDF.

## Título

Selección de la tecnología de mapeo objeto-relacional (ORM) para la capa de persistencia de BIOPET.

## Estado

Aceptado.

## Contexto

BIOPET necesita persistir dos entidades principales, `Usuario` y `Mascota`, relacionadas por una clave foránea (`duenio_id`), contra PostgreSQL 16, con soporte para paginación, ordenamiento y filtros dinámicos por nombre, especie y raza. El stack del PFC ya fija Java 21 y Spring Boot 3.x como backend, lo cual acota de entrada las opciones de ORM disponibles a las del ecosistema Java/JVM, pero se documentan también dos alternativas de otros ecosistemas ampliamente usadas en la industria para justificar la decisión con criterios objetivos y no solo por default del framework.

## Decisión

Se usa **Hibernate como implementación de JPA, integrado mediante Spring Data JPA**, para toda la capa de persistencia de BIOPET.

## Alternativas comparadas

- **Doctrine ORM:** el ORM de referencia del ecosistema PHP (usado típicamente con Symfony), que mapea clases PHP a tablas mediante anotaciones o atributos y expone su propio lenguaje de consultas orientado a objetos, DQL, inspirado directamente en el HQL de Hibernate [1].
- **Entity Framework Core (EF Core):** el ORM oficial de Microsoft para .NET, que mapea clases C# a tablas mediante *Fluent API* o *Data Annotations*, y expone LINQ como lenguaje de consulta integrado en el propio lenguaje C# [2].
- **Hibernate / JPA (con Spring Data JPA):** la implementación de referencia de la especificación JPA para Java, integrada de forma nativa en el ecosistema Spring Boot mediante Spring Data JPA, que añade generación automática de repositorios, paginación y especificaciones dinámicas sobre Hibernate [3].

## Criterios de comparación

| Criterio | Doctrine (PHP) | EF Core (.NET) | Hibernate/JPA (Java) |
|---|---|---|---|
| Lenguaje/ecosistema | PHP, típicamente con Symfony | C#/.NET | Java/Kotlin, típicamente con Spring |
| Lenguaje de consulta propio | DQL | LINQ (integrado en el lenguaje) | JPQL / Criteria API |
| Integración con el framework backend del PFC | Ninguna (ecosistema distinto a Spring Boot) | Ninguna (ecosistema distinto a Spring Boot) | Nativa, vía Spring Data JPA |
| Generación de repositorios a partir de convenciones | Parcial, mediante `EntityRepository` personalizados | Parcial, mediante `DbSet<T>` y LINQ | Alta, `JpaRepository`/`JpaSpecificationExecutor` generan CRUD, paginación y filtros dinámicos sin código adicional |
| Migraciones de esquema | Migrations propias de Doctrine | Migrations propias de EF Core | Se delega a una herramienta externa (Flyway/Liquibase), decisión ya justificada en `ADR-004-hibernate-jpa-flyway.md` |
| Madurez y comunidad en el stack del PFC | Alta en PHP, nula en Java | Alta en .NET, nula en Java | Alta, es la implementación de referencia de JPA para el ecosistema exigido por el proyecto |

## Por qué se eligió Hibernate/JPA con Spring Data JPA

La decisión no es solo una preferencia técnica sino una consecuencia directa de que el backend del PFC ya está fijado en Java 21 y Spring Boot 3.x: Doctrine y EF Core pertenecen a ecosistemas de lenguaje completamente distintos (PHP y .NET respectivamente) y no podrían integrarse con ese backend sin cambiar el lenguaje del proyecto entero. Dentro del ecosistema Java, Hibernate es la implementación de JPA con mayor adopción y la que Spring Data JPA integra de forma nativa, lo que permite que `MascotaRepository` y `UsuarioRepository` obtengan operaciones CRUD, paginación (`Pageable`, `Page<T>`) y filtros dinámicos (`JpaSpecificationExecutor`, usado en `MascotaSpecifications`) heredando de interfaces estándar, sin escribir una capa de acceso a datos manual equivalente a la que exigiría Doctrine o EF Core en sus propios ecosistemas.

## Ventajas

- Integración nativa con Spring Boot: no se requiere código de configuración adicional para conectar el ORM con el resto del framework.
- Generación automática de repositorios CRUD, paginación y consultas dinámicas mediante Spring Data JPA, reduciendo drásticamente el código repetitivo frente a escribir SQL manual.
- Ecosistema maduro con amplia documentación y comunidad, lo que facilita resolver problemas durante el desarrollo del PFC.
- Coherente con la decisión ya tomada de usar Flyway para migraciones versionadas (`ddl-auto: validate`), evitando que el ORM controle el esquema de producción.

## Limitaciones

- Las asociaciones perezosas (`FetchType.LAZY`) requieren atención consciente para evitar el problema N+1, como el identificado en `MascotaService.listar(...)` y documentado en `06-orm-jpa.md`.
- Curva de aprendizaje mayor que escribir SQL directo para quien no conoce el ciclo de vida de entidades de JPA (estados *transient*, *managed*, *detached*).
- El comportamiento exacto del SQL generado depende del dialecto de Hibernate configurado para PostgreSQL, lo que exige revisar los logs de SQL generado cuando el rendimiento de una consulta no es el esperado.

## Consecuencias

Se mantiene una única tecnología de persistencia (Hibernate/Spring Data JPA) en todo el backend, coherente con las entidades `Usuario` y `Mascota` ya implementadas y con la estrategia de migraciones Flyway del `ADR-004`. No se introduce ninguna dependencia hacia Doctrine o EF Core, que quedan descartadas por pertenecer a ecosistemas de lenguaje incompatibles con el stack ya definido del PFC, no por una deficiencia técnica propia de esas herramientas.

## Referencias

[1] Doctrine Project, "Doctrine ORM documentation." [Online]. Available: https://www.doctrine-project.org/projects/doctrine-orm/en/current/index.html

[2] Microsoft, "Entity Framework Core documentation." [Online]. Available: https://learn.microsoft.com/en-us/ef/core/

[3] Red Hat / Hibernate Team, "Hibernate ORM user guide." [Online]. Available: https://hibernate.org/orm/documentation/
