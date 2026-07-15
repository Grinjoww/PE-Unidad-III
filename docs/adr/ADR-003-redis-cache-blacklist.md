# ADR-003: Redis para caché y blacklist de JWT

## Estado

Propuesta (pendiente de confirmar detalles de implementación con Jaime)

## Contexto

El PFC necesita reducir la latencia de las consultas de lectura más frecuentes sin modificar PostgreSQL como fuente de verdad, y necesita también una forma de invalidar tokens JWT antes de su expiración natural cuando el usuario cierra sesión, ya que un JWT firmado sigue siendo válido hasta que expira por sí solo, sin importar si el usuario ya cerró sesión. Redis es un almacén de estructuras de datos en memoria, lo que lo hace considerablemente más rápido que una consulta a PostgreSQL para datos de acceso frecuente, y admite expiración automática por TTL en cada clave, lo cual resuelve de forma natural ambos problemas: datos de caché que deben caducar solos, y tokens en blacklist que deben desaparecer exactamente cuando el JWT original habría expirado de todas formas.

## Decisión

Se usa Redis 7 para dos propósitos independientes dentro del PFC:

**Uso 1: Caché de consultas (patrón cache-aside).** Los endpoints de lectura frecuente se cachean con `@Cacheable`, usando como clave los parámetros relevantes de cada consulta (página, tamaño, filtros aplicados). *(Pendiente confirmar con Jaime: nombre exacto de la cache, formato final de la clave, y TTL configurado.)*

**Uso 2: Blacklist de JTI en logout.** Al cerrar sesión, se extrae el JTI (identificador único) del JWT y se guarda como clave en Redis con un TTL igual al tiempo de vida restante del token, de modo que la entrada se autoelimina cuando el token habría expirado de todas formas. El filtro de seguridad consulta Redis en cada petición antes de aceptar un token. *(Pendiente confirmar con Jaime: formato exacto de la clave del JTI.)*

**Invalidación de caché.** Las escrituras (crear, actualizar, eliminar) disparan `@CacheEvict` sobre las entradas afectadas. *(Pendiente confirmar con Jaime: si se usa `allEntries = true` o invalidación por clave específica, y qué listados exactos se invalidan.)* Esto implica un riesgo aceptado de consistencia: entre el momento de la escritura y la invalidación, una lectura podría servir una versión ligeramente obsolesta si no se invalida de forma síncrona.

**Disponibilidad.** *(Pendiente confirmar con Jaime: qué ocurre si Redis no está disponible — si la aplicación debe seguir funcionando consultando directamente PostgreSQL, aceptando mayor latencia, o si debe fallar la petición.)* La expectativa de diseño es que Redis sea una optimización, no una dependencia crítica: su caída no debería tumbar el sistema, solo hacerlo más lento.

## Consecuencias

**Positivas:**
- Reduce la carga sobre PostgreSQL en los endpoints de lectura más consultados.
- Resuelve el problema de revocación de JWT sin necesidad de sesiones con estado en el servidor.
- El TTL automático evita que la blacklist crezca indefinidamente sin necesidad de un proceso de limpieza manual.

**Negativas:**
- Introduce una pieza de infraestructura adicional que el equipo debe operar y mantener disponible.
- Existe una ventana breve de inconsistencia entre una escritura y la invalidación efectiva de la caché.
- Si Redis falla y la aplicación no está preparada para degradarse con gracia, el logout podría dejar de invalidar tokens correctamente hasta que Redis se recupere.

## Alternativas consideradas

**Caché local (en memoria de la propia aplicación, sin Redis).** Descartada porque no se comparte entre instancias si el backend llegara a escalar horizontalmente, y no resuelve el problema de la blacklist de JWT.

**Usar PostgreSQL también para la blacklist.** Descartada porque obligaría a implementar manualmente la expiración (un job de limpieza periódico), en vez de aprovechar el TTL nativo de Redis, y añadiría carga de escritura a la base de datos principal en cada logout.

**No usar blacklist (aceptar que el JWT siga siendo válido hasta expirar).** Descartada porque compromete la seguridad: un token robado o filtrado seguiría siendo utilizable después de que el usuario legítimo cerró sesión.

**Tokens de vida muy corta en vez de blacklist.** Descartada porque, aunque reduciría la ventana de riesgo, obligaría a refrescar el token con mucha frecuencia, empeorando la experiencia de usuario sin eliminar el problema de fondo.

**Sesiones completas gestionadas por el servidor (en vez de JWT).** Descartada porque abandona las ventajas de JWT (statelessness, escalabilidad horizontal sin sesiones compartidas) que motivaron su elección original para este PFC.
