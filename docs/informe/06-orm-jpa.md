# ORM y mapeo objeto-relacional

**Equipo:** Equipo H
**Proyecto:** Java 21 + Spring Boot 3.x + Angular 17+ + PostgreSQL 16 + Redis
**Autor:** Carvajal Loor Johan Stalin

## 1. Qué es un ORM y el impedance mismatch

Un ORM (*Object-Relational Mapper*) es una capa de software que traduce automáticamente entre el modelo relacional de una base de datos —tablas, filas, claves foráneas y joins— y el modelo de objetos de un lenguaje orientado a objetos —clases, instancias y asociaciones entre ellas. Esta traducción existe porque ambos modelos representan la información de forma estructuralmente distinta, un desajuste conocido como *impedance mismatch* (Fowler, 2002): una tabla no tiene herencia, un join no es lo mismo que una referencia entre objetos, y una fila no tiene comportamiento propio, mientras que una clase sí puede tener métodos, jerarquías y relaciones bidireccionales. El ORM existe para que el desarrollador trabaje con clases y objetos —como `Usuario` y `Mascota` en este proyecto— sin escribir manualmente el SQL que traduce cada operación sobre esos objetos a filas de las tablas `usuarios` y `mascotas`.

## 2. Estrategias de mapeo de herencia

Cuando el modelo de dominio tiene jerarquías de clases, JPA ofrece tres estrategias para mapearlas a tablas relacionales:

- **Single Table:** todas las subclases de una jerarquía se almacenan en una única tabla, con una columna discriminadora que indica el tipo de cada fila. Es la opción más rápida en lectura (sin joins) pero genera columnas nulas para los atributos que no aplican a todas las subclases.
- **Joined (Class Table Inheritance):** cada clase de la jerarquía —incluida la superclase— tiene su propia tabla, y recuperar una subclase completa requiere un join entre la tabla base y la de la subclase. Minimiza columnas nulas a costa de más joins.
- **Table per Concrete Class:** cada subclase concreta tiene su propia tabla con todas las columnas, incluidas las heredadas, sin tabla para la superclase abstracta. Evita joins entre niveles, pero duplica columnas y complica las consultas polimórficas sobre toda la jerarquía.

Este PFC no presenta actualmente una jerarquía de herencia entre entidades: `Usuario` y `Mascota` son dos entidades planas relacionadas por una clave foránea (`duenio_id`), no una jerarquía de subclases. La comparación anterior se documenta como fundamento teórico exigido por la guía y como criterio a aplicar si el dominio creciera —por ejemplo, si `Usuario` se especializara en subtipos con atributos propios.

## 3. ORM frente a SQL puro y query builders

| Criterio | ORM (JPA/Hibernate) | SQL puro (JDBC) | Query builder |
|---|---|---|---|
| Curva de aprendizaje | Media-alta: requiere entender el ciclo de vida de entidades, *lazy loading* y el contexto de persistencia | Baja: solo requiere conocer SQL | Media: sintaxis propia sobre SQL |
| Consultas simples | Muy rápidas de escribir (`findById`, `findAll`) | Repetitivas, mucho código boilerplate | Rápidas, con seguridad de tipos |
| Consultas complejas | Puede volverse difícil de optimizar sin control fino | Control total sobre el SQL exacto | Buen equilibrio, aunque limitado frente a SQL avanzado |
| Portabilidad entre motores de BD | Alta, el ORM abstrae el dialecto SQL | Baja, el SQL suele ser específico del motor | Alta, similar al ORM |
| Migraciones de esquema | Requiere herramienta externa (Flyway/Liquibase) o generación automática | Manual, control total | Igual que el ORM |
| Depuración | Requiere inspeccionar el SQL generado (logs de Hibernate) | Directa, el SQL es explícito | Intermedia |
| Rendimiento | Bueno en el caso general; requiere ajuste consciente en consultas de alto volumen | Óptimo si el SQL está bien escrito | Cercano a SQL puro |

## 4. Lazy loading y eager loading

El **lazy loading** (carga perezosa) pospone la consulta de una asociación hasta que el código accede explícitamente a ella; el **eager loading** (carga ansiosa) trae la asociación completa junto con la entidad principal, en la misma consulta o en una inmediata. En la entidad `Mascota` de este proyecto, la relación hacia `Usuario` está declarada explícitamente como perezosa:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "duenio_id", nullable = false)
private Usuario duenio;
```

Esto significa que `mascotaRepository.findAll(...)` no trae los datos del dueño en la misma consulta; el dueño solo se consulta contra la base de datos en el momento en que algún código llama a `mascota.getDuenio()`.

## 5. El problema N+1 y su presencia real en el proyecto

El problema N+1 ocurre cuando se ejecuta una consulta para traer una lista de N entidades y, por cada una de ellas, se dispara una consulta adicional para resolver una asociación perezosa, resultando en N+1 consultas totales en lugar de una sola optimizada. En `MascotaService`, el método `listar(...)` pagina resultados de `Mascota` y los convierte a `MascotaResponse` mediante `toResponse(...)`:

```java
private MascotaResponse toResponse(Mascota mascota) {
    return new MascotaResponse(
            mascota.getId(),
            mascota.getDuenio().getId(),
            mascota.getDuenio().getNombre(),
            ...
    );
}
```

Como `duenio` es `LAZY`, cada llamada a `mascota.getDuenio()` dentro del `.map(this::toResponse)` sobre una página de resultados dispara, en el peor de los casos, una consulta adicional por cada mascota de la página para resolver su dueño —el patrón clásico N+1— salvo que Hibernate reutilice una entidad `Usuario` ya cargada en el mismo contexto de persistencia. Esto convierte a `listar(...)` en un candidato real y verificable para optimización, no un ejemplo hipotético.

## 6. Soluciones al problema N+1

- **JOIN FETCH:** una consulta JPQL explícita (`SELECT m FROM Mascota m JOIN FETCH m.duenio WHERE ...`) que trae la entidad principal y su asociación en una sola sentencia SQL con `INNER JOIN`.
- **@EntityGraph:** una anotación declarativa sobre el método del repositorio que indica qué asociaciones cargar de forma ansiosa para esa consulta específica, sin cambiar el tipo de *fetch* por defecto de la entidad.
- **DTO projections:** en vez de traer la entidad completa y mapearla después, se proyecta directamente a un DTO (por ejemplo, mediante una interfaz de proyección de Spring Data o una consulta JPQL con `new` constructor expression) que selecciona solo las columnas necesarias, incluidas las del dueño, en una única consulta con join.

Para el listado paginado de `MascotaService`, la opción más natural sería añadir un `@EntityGraph(attributePaths = "duenio")` al método correspondiente en `MascotaRepository`, o una proyección JPQL que seleccione directamente los campos de `MascotaResponse` con join a `usuarios`, evitando así el N+1 sin sacrificar la paginación de `Pageable`.

## 7. Hibernate, JPA, Spring Data JPA, Repository, Pageable y Page&lt;T&gt;

- **JPA (Jakarta Persistence API):** la especificación estándar de Java que define cómo mapear clases a tablas mediante anotaciones (`@Entity`, `@Id`, `@ManyToOne`), sin implementar el comportamiento por sí misma.
- **Hibernate:** la implementación concreta de JPA usada en este proyecto; traduce las operaciones sobre entidades anotadas en sentencias SQL reales contra PostgreSQL.
- **Spring Data JPA:** una capa adicional sobre JPA/Hibernate que genera automáticamente la implementación de interfaces de repositorio a partir de convenciones de nombres de métodos o especificaciones, evitando escribir manualmente el código de acceso a datos.
- **Repository:** la interfaz que declara las operaciones de persistencia disponibles para una entidad. `MascotaRepository extends JpaRepository<Mascota, Long>, JpaSpecificationExecutor<Mascota>` hereda operaciones CRUD estándar y permite construir consultas dinámicas mediante `Specification`, como se usa en `MascotaSpecifications` para combinar filtros de nombre, especie y raza.
- **Pageable:** un objeto que encapsula el número de página, el tamaño de página y el criterio de ordenamiento de una consulta paginada.
- **Page&lt;T&gt;:** el resultado de una consulta paginada, que además del contenido de la página actual incluye metadatos como el total de elementos y el total de páginas, usado en `MascotaService.listar(...)` antes de transformarse en el DTO `PaginaResponse`.

## 8. Migraciones: Flyway, Liquibase y `ddl-auto=update`

Flyway y Liquibase son herramientas de migración versionada: cada cambio de esquema se escribe como un archivo numerado (`V1__schema_inicial.sql`, `V2__ajustes_modelo_mascotas.sql`, `V3__datos_semilla.sql` en este proyecto) que se ejecuta una sola vez, en orden, y queda registrado en una tabla de historial dentro de la propia base de datos. Flyway usa principalmente SQL plano por versión, mientras que Liquibase añade la posibilidad de describir los cambios en XML, YAML o JSON de forma más abstracta y portable entre motores de base de datos, a costa de una capa adicional de indirección sobre el SQL final. Frente a ambas, `ddl-auto=update` de Hibernate genera y ajusta el esquema automáticamente a partir de las entidades anotadas, sin dejar un historial versionado ni control explícito sobre qué cambió y cuándo.

## 9. Por qué `ddl-auto=update` no es apropiado en producción

`ddl-auto=update` infiere el esquema a partir del estado actual de las clases Java, lo que significa que el esquema de la base de datos queda acoplado silenciosamente a refactorizaciones del código: renombrar un campo, cambiar un tipo o eliminar una entidad puede alterar o eliminar columnas sin ningún registro explícito de qué ocurrió ni posibilidad de revertirlo de forma controlada. Tampoco garantiza que el cambio sea seguro para datos ya existentes —por ejemplo, no sabe si una columna nueva debe tener un valor por defecto para las filas ya insertadas—, ni dos entornos ejecutando la misma versión de la aplicación quedan garantizados de tener el mismo esquema si Hibernate infiere el DDL de forma distinta. Por eso, el proyecto configura explícitamente `spring.jpa.hibernate.ddl-auto: validate` (que solo verifica que las entidades correspondan al esquema existente, sin modificarlo) y delega la creación real del esquema a Flyway, dejando un historial auditable y reproducible de cada cambio.

## 10. Relación con la arquitectura Controller → Service → Repository → Entity

En este PFC, el ORM se integra naturalmente en la arquitectura por capas: el `Controller` (por ejemplo, `MascotaController`) recibe la petición HTTP y la delega sin lógica de negocio; el `Service` (`MascotaService`) coordina la transacción, aplica reglas como la validación del criterio de ordenamiento permitido y decide cuándo usar la caché de Redis; el `Repository` (`MascotaRepository`) expone las operaciones de persistencia a través de Spring Data JPA y `Specification` para los filtros dinámicos; y la `Entity` (`Mascota`, `Usuario`) representa el modelo de dominio persistente, con Hibernate como implementación de JPA responsable de traducir esas entidades a sentencias SQL contra las tablas `mascotas` y `usuarios` creadas por las migraciones Flyway.

## Referencias

Fowler, M. (2002). *Patterns of enterprise application architecture*. Addison-Wesley.

