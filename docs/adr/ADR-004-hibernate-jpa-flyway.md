# ADR-004: Hibernate, Spring Data JPA y Flyway como estrategia de persistencia y migraciones

## Estado

Aceptado.

## Contexto

El enunciado asigna formalmente el ADR-002 a la decisión de frontend (Angular frente a React). Sin embargo, el PFC toma dos decisiones arquitectónicas igual de relevantes en la capa de persistencia —qué tecnología de acceso a datos usar y cómo versionar el esquema de PostgreSQL— que merecen su propio registro para no perder la trazabilidad del razonamiento. Este ADR adicional documenta esas dos decisiones: la elección de Hibernate/Spring Data JPA como capa de acceso a datos, y la elección de Flyway como mecanismo de migraciones versionadas, ambas ya reflejadas en el código del backend (`pom.xml`, `application.yml`, `db/migration/`).

## Decisión

Se usa **Hibernate** como implementación de JPA, **Spring Data JPA** para la capa de repositorios, paginación y consultas dinámicas, **Flyway** para migraciones de esquema versionadas, y **PostgreSQL 16** como motor de persistencia relacional.

## Comparación: JDBC puro, SQL manual, Hibernate/JPA y Spring Data JPA

| Criterio | JDBC puro / SQL manual | Hibernate/JPA | Spring Data JPA |
|---|---|---|---|
| Código necesario por operación CRUD | Alto: `PreparedStatement`, mapeo manual de `ResultSet` a objetos | Medio: se define la entidad una vez, Hibernate genera el SQL | Bajo: heredar `JpaRepository` da CRUD, paginación y ordenamiento sin escribir SQL |
| Control sobre el SQL exacto | Total | Parcial, mediado por el dialecto de Hibernate | Parcial, salvo con `@Query` o `Specification` explícitas |
| Riesgo de errores de mapeo manual | Alto | Bajo, el mapeo objeto-columna es declarativo (`@Column`, `@JoinColumn`) | Bajo, hereda las garantías de JPA |
| Consultas dinámicas con filtros opcionales | Requiere construir el SQL condicionalmente a mano | Posible con Criteria API, verboso | Directo con `JpaSpecificationExecutor` y `Specification`, como en `MascotaSpecifications` de este proyecto |

## Comparación: Flyway, Liquibase y generación automática del esquema

| Criterio | Flyway | Liquibase | `ddl-auto=update` |
|---|---|---|---|
| Formato de los cambios | SQL plano versionado (`V1__...sql`) | XML, YAML, JSON o SQL | Ninguno; se infiere del código de las entidades |
| Historial auditable | Sí, tabla de historial en la propia base de datos | Sí, changelog propio | No existe historial explícito |
| Reversión controlada | Requiere migraciones de bajada escritas a mano (no usado en este proyecto) | Soporta rollback declarativo | No aplica; los cambios se sobrescriben silenciosamente |
| Riesgo en producción | Bajo, cada cambio es explícito y revisable en Pull Request | Bajo, mismo principio que Flyway | Alto: cambios no versionados, no reproducibles entre entornos |
| Curva de aprendizaje | Baja para quien ya conoce SQL | Media, por la capa de abstracción adicional | Ninguna, pero ese es justamente el problema |

## Decisiones específicas

- **Hibernate** como implementación de JPA: es la implementación de referencia del ecosistema Spring Boot, con soporte first-class para PostgreSQL y para el manejo de asociaciones perezosas (`FetchType.LAZY`) usado en la relación `Mascota → Usuario`.
- **Spring Data JPA** para `Repository`, paginación y consultas: `MascotaRepository extends JpaRepository<Mascota, Long>, JpaSpecificationExecutor<Mascota>` obtiene CRUD y paginación (`Pageable`, `Page<T>`) sin código adicional, y `JpaSpecificationExecutor` permite construir filtros combinables (nombre, especie, raza) sin una consulta distinta por cada combinación posible de filtros.
- **Flyway** para migraciones versionadas y reproducibles: el esquema se construye mediante `V1__schema_inicial.sql` (tablas `usuarios` y `mascotas`, restricciones, índices y triggers de `actualizado_en`), `V2__ajustes_modelo_mascotas.sql` y `V3__datos_semilla.sql`, ejecutadas en orden y registradas en el historial de Flyway (`spring.flyway.enabled: true`, `baseline-on-migrate: true`).
- **PostgreSQL 16** como persistencia relacional: exigido por el enunciado del PFC y coherente con el uso de restricciones (`CHECK`, claves foráneas) y triggers definidos directamente en las migraciones V1.
- Complementariamente, `spring.jpa.hibernate.ddl-auto` se configura como **`validate`** (no `update`): Hibernate verifica que las entidades correspondan al esquema creado por Flyway, pero nunca modifica el esquema por sí mismo.

## Consecuencias

**Positivas:**

- El esquema de PostgreSQL queda versionado junto al código fuente, con un historial explícito de qué cambió, cuándo y en qué migración.
- `ddl-auto: validate` detecta en tiempo de arranque cualquier desalineación entre las entidades Java y el esquema real, evitando que un cambio de entidad se propague silenciosamente a producción.
- Los filtros dinámicos de `MascotaSpecifications` evitan escribir una consulta distinta por cada combinación de nombre, especie y raza.

**Negativas y riesgos:**

- Las asociaciones perezosas (`FetchType.LAZY`) requieren atención consciente para evitar el problema N+1 documentado en la sección de ORM (`06-orm-jpa.md`), particularmente en `MascotaService.listar(...)`.
- Cualquier cambio de esquema exige escribir una nueva migración Flyway (`V4__...`); no existe atajo de generación automática, lo que añade un paso manual pero deliberado al flujo de trabajo.
- Flyway no incluye rollback automático de una migración ya aplicada: revertir un cambio requiere escribir una migración nueva que deshaga el anterior.

## Alternativas descartadas

- **JDBC puro / SQL manual:** descartado por el volumen de código repetitivo que exigiría para el CRUD de `Usuario` y `Mascota`, sin aportar ventajas relevantes para el alcance académico del PFC.
- **Liquibase:** descartado en favor de Flyway por preferir SQL plano y versionado directo sobre una capa de abstracción adicional (XML/YAML), dado que el equipo ya domina SQL y no necesita portabilidad entre distintos motores de base de datos.
- **`ddl-auto=update` como estrategia principal:** descartado por no dejar historial auditable ni control explícito sobre los cambios de esquema, tal como se argumenta en la sección 9 de `06-orm-jpa.md`.

## Referencias

Flyway. (s.f.). *Flyway documentation*. Redgate. https://documentation.red-gate.com/flyway

Fowler, M. (2002). *Patterns of enterprise application architecture*. Addison-Wesley.
