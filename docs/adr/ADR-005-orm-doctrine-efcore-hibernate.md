# ADR-005: Selección de Hibernate/JPA como tecnología de persistencia frente a Doctrine y Entity Framework Core

## Estado

Aceptado.

## Contexto

El PFC exige una capa de persistencia orientada a objetos sobre PostgreSQL 16 para las entidades `Usuario` y `Mascota`, con soporte de asociaciones (`Mascota → Usuario`), consultas dinámicas con filtros combinables (nombre, especie, raza) y paginación. Antes de comprometerse con una implementación concreta dentro del ecosistema Java, el equipo evaluó las tres opciones de ORM más representativas del mercado, cada una asociada a un stack tecnológico distinto: **Doctrine** (PHP), **Entity Framework Core** (.NET/C#) y **Hibernate/JPA** (Java). Esta decisión es independiente de la comparación interna ya documentada en ADR-004 (Hibernate/JPA frente a JDBC puro, Spring Data JPA y las herramientas de migración Flyway/Liquibase): aquí se compara Hibernate contra ORMs equivalentes de otros lenguajes, no contra alternativas dentro del propio ecosistema Java.

El stack del proyecto ya está fijado por el enunciado del PFC en Java 21 + Spring Boot 3.2.12 (ADR-001), lo que en la práctica orienta la decisión hacia el ecosistema Java. Aun así, el equipo documenta la comparación completa para justificar objetivamente que Hibernate/JPA no es solo "la opción del lenguaje elegido", sino una decisión técnicamente sólida por derecho propio.

## Decisión

Se usa **Hibernate** como implementación de JPA (Jakarta Persistence API), integrado mediante **Spring Data JPA**, como tecnología de persistencia objeto-relacional para BioPet.

## Comparación: Doctrine, Entity Framework Core e Hibernate/JPA

| Criterio | Doctrine (PHP) | Entity Framework Core (.NET) | Hibernate/JPA (Java) |
|---|---|---|---|
| Modelo de especificación | Implementación propia, sin estándar formal del lenguaje | Propietario de Microsoft, sin especificación independiente del proveedor | Implementación de referencia de una especificación estándar (JPA/Jakarta EE), lo que permite sustituir Hibernate por otro proveedor (p. ej. EclipseLink) sin reescribir el código de dominio |
| Mapeo de entidades | Anotaciones PHP 8 o XML/YAML externo | Fluent API en código C# o *Data Annotations* | Anotaciones Jakarta Persistence (`@Entity`, `@Column`, `@ManyToOne`) directamente sobre la clase, como en `Usuario` y `Mascota` de este proyecto |
| Gestión de asociaciones perezosas/ansiosas | Soporta *lazy loading* mediante proxies | Requiere habilitar explícitamente *lazy loading* con proxies o usar `Include()` para carga ansiosa | `FetchType.LAZY`/`EAGER` declarativo por asociación, ya usado en `Mascota.duenio` |
| Consultas dinámicas con filtros combinables | DQL (Doctrine Query Language) o `QueryBuilder` | LINQ integrado en el lenguaje | `Specification`/Criteria API; el proyecto usa `JpaSpecificationExecutor` en `MascotaSpecifications` para combinar filtros de nombre, especie y raza sin una consulta por combinación |
| Migraciones de esquema | Doctrine Migrations (herramienta propia del ecosistema) | EF Core Migrations, integradas en el mismo framework | Sin herramienta de migraciones propia; se combina con Flyway o Liquibase (ADR-004), lo que separa la responsabilidad de persistencia de la de versionado de esquema |
| Curva de aprendizaje si el equipo ya conoce el lenguaje base | Baja para equipos PHP | Baja para equipos .NET | Baja para este equipo, ya que el resto del stack (Spring Boot, Spring Security) es Java |
| Madurez y ecosistema | Maduro dentro de Symfony/PHP, comunidad menor fuera de ese ecosistema | Maduro, fuertemente ligado al ecosistema Microsoft | Maduro, con más de dos décadas de uso en producción y adopción estándar en aplicaciones empresariales Java |
| Portabilidad del conocimiento | Limitado a PHP | Limitado a .NET | JPA es un estándar; el conocimiento de anotaciones y ciclo de vida de entidades es transferible entre implementaciones (Hibernate, EclipseLink) |

## Por qué se eligió Hibernate/JPA con Spring Data JPA

- **Coherencia con el stack ya decidido.** El backend usa Spring Boot 3.2.12 sobre Java 21 (ADR-001); Hibernate es la implementación de JPA integrada por defecto en Spring Boot, sin necesidad de configuración adicional de terceros. Elegir Doctrine o EF Core habría exigido migrar todo el backend a PHP o .NET, algo fuera del alcance y del enunciado del PFC.
- **Especificación estándar frente a implementación propietaria.** A diferencia de Doctrine y EF Core, JPA es una especificación (Jakarta EE) con múltiples implementaciones intercambiables; el código de las entidades (`@Entity`, `@Id`, `@ManyToOne`) no depende de la sintaxis particular de Hibernate, lo que reduce el acoplamiento a un único proveedor.
- **Integración directa con Spring Data JPA.** `MascotaRepository extends JpaRepository<Mascota, Long>, JpaSpecificationExecutor<Mascota>` obtiene CRUD, paginación (`Pageable`, `Page<T>`) y filtros dinámicos sin escribir código de acceso a datos adicional, con un nivel de productividad equivalente al que ofrecen Doctrine y EF Core en sus respectivos ecosistemas.
- **Separación de responsabilidades entre persistencia y versionado de esquema.** A diferencia de Doctrine Migrations y EF Core Migrations —que generan migraciones a partir del propio ORM—, este proyecto usa Flyway de forma independiente (ADR-004), lo que evita que un cambio en las entidades Java dispare automáticamente una alteración del esquema de producción.

## Ventajas

- Curva de aprendizaje baja para el equipo, ya familiarizado con Java y Spring Boot.
- Especificación estándar (JPA) que evita el acoplamiento total a un único proveedor de ORM.
- Ecosistema maduro con soporte first-class para PostgreSQL, `Specification` para filtros dinámicos y paginación integrada.
- Documentación y comunidad amplias, al ser el ORM más usado en aplicaciones empresariales Java.

## Limitaciones

- Requiere atención consciente al *fetch type* de cada asociación para evitar el problema N+1, documentado en `docs/informe/06-orm-jpa.md`.
- No incluye herramienta de migraciones propia, a diferencia de Doctrine Migrations y EF Core Migrations; obliga a incorporar Flyway o Liquibase como pieza adicional.
- La curva de aprendizaje de JPA (ciclo de vida de entidades, contexto de persistencia, *lazy loading*) es más pronunciada que la de un query builder simple, aunque comparable a la de Doctrine y EF Core en sus respectivos lenguajes.

## Alternativas descartadas

- **Doctrine:** descartado por requerir PHP/Symfony, fuera del stack exigido por el enunciado del PFC (Java 21 + Spring Boot).
- **Entity Framework Core:** descartado por requerir .NET/C#, igualmente fuera del stack exigido; además, LINQ y las migraciones de EF Core no son transferibles al resto del backend Java del proyecto.

## Consecuencias

**Positivas:** persistencia coherente con el resto del stack Java, especificación estándar que facilita cambiar de implementación de JPA si fuera necesario, y productividad equivalente a la de los ORMs de otros lenguajes gracias a Spring Data JPA.

**Negativas:** el equipo asume el problema N+1 como riesgo inherente a cualquier ORM con asociaciones perezosas, y depende de una herramienta externa (Flyway) para el versionado de esquema, a diferencia de Doctrine y EF Core que integran sus propias migraciones.

## Referencias

[1] Object Management Group / Eclipse Foundation, "Jakarta Persistence Specification," Jakarta EE, 2022. [En línea]. Disponible: https://jakarta.ee/specifications/persistence/

[2] Doctrine Project, "Doctrine ORM documentation." [En línea]. Disponible: https://www.doctrine-project.org/projects/orm.html

[3] Microsoft, "Entity Framework Core documentation." [En línea]. Disponible: https://learn.microsoft.com/en-us/ef/core/

[4] Red Hat, "Hibernate ORM user guide." [En línea]. Disponible: https://hibernate.org/orm/documentation/