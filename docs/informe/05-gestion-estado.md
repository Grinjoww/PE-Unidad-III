# Gestión de estado en aplicaciones web

**Equipo:** Equipo H
**Proyecto:** Java 21 + Spring Boot 3.x + Angular 17+ + PostgreSQL 16 + Redis
**Autor:** Carvajal Loor Johan Stalin

## 1. HTTP como protocolo sin estado

El protocolo HTTP se diseñó como un protocolo *stateless*: cada petición que un cliente envía a un servidor se procesa de forma completamente independiente, sin que el servidor conserve memoria de peticiones anteriores del mismo cliente (Fielding, 2000). El ciclo petición-respuesta es simple en apariencia —el cliente abre una conexión, envía una petición con un método, una URL y encabezados, y el servidor responde con un código de estado y un cuerpo— pero esa independencia entre peticiones es justamente lo que obliga a construir mecanismos externos cuando una aplicación necesita "recordar" quién es el usuario entre una petición y la siguiente.

## 2. La ausencia de estado: ventaja de escalabilidad, problema de continuidad

Que el servidor no guarde estado por cliente es lo que permite que cualquier instancia del backend pueda atender cualquier petición entrante, sin importar cuál instancia atendió la petición anterior. Esto simplifica enormemente el escalado horizontal: basta con levantar más réplicas detrás de un balanceador de carga, sin coordinación adicional entre ellas. El costo de esa simplicidad aparece en cuanto la aplicación necesita continuidad: un carrito de compras, una sesión autenticada, las preferencias de un usuario o el contenido de un formulario de varios pasos requieren que el servidor (o el cliente) conserve información entre peticiones. HTTP no ofrece esto de forma nativa, por lo que toda aplicación web con autenticación —como el proyecto BioPet de este PFC— necesita decidir dónde y cómo se guarda ese estado.

## 3. Alternativas para conservar estado entre peticiones

Existen cuatro estrategias principales:

- **Cookies:** un identificador (o directamente los datos) que el navegador reenvía automáticamente en cada petición al mismo dominio.
- **Sesiones de servidor:** el servidor guarda los datos del usuario en memoria o en un almacén compartido, y solo envía al cliente un identificador de sesión, normalmente mediante cookie.
- **Tokens JWT (JSON Web Token):** un token firmado que contiene la identidad del usuario y sus claims, y que el propio cliente conserva y reenvía; el servidor no necesita guardar nada para validarlo, solo verificar la firma (Jones et al., 2015).
- **Estado en base de datos:** los datos de sesión o de negocio se persisten directamente en una tabla relacional, consultándose en cada petición.

La diferencia clave entre las tres primeras no es dónde viaja el dato, sino quién es responsable de conservarlo: las sesiones de servidor centralizan el estado en el backend, los JWT lo delegan al cliente (con firma que impide su alteración), y las cookies son simplemente el vehículo de transporte que puede llevar cualquiera de los dos.

## 4. localStorage, sessionStorage y cookies

| Característica | localStorage | sessionStorage | Cookies |
|---|---|---|---|
| Capacidad aproximada | ~5-10 MB | ~5-10 MB | ~4 KB |
| Persistencia | Permanece hasta borrado manual | Se borra al cerrar la pestaña | Definida por `Expires`/`Max-Age`, puede ser de sesión o persistente |
| Acceso desde JavaScript | Sí, siempre accesible por `window.localStorage` | Sí, siempre accesible por `window.sessionStorage` | Solo si NO tiene el atributo `HttpOnly` |
| Envío automático al servidor | No; hay que adjuntarlo manualmente en cada petición | No; igual que localStorage | Sí, el navegador lo adjunta automáticamente en cada petición al dominio correspondiente |
| Expuesto a robo por XSS | Alto: cualquier script inyectado puede leerlo | Alto, mismo riesgo que localStorage | Bajo si es `HttpOnly`, ya que JavaScript no puede leerlo |
| Expuesto a CSRF | No, porque no se envía automáticamente | No | Sí, salvo que se mitigue con `SameSite` y/o tokens anti-CSRF |

## 5. Por qué BioPet guarda el JWT en una cookie HttpOnly y no en localStorage

Guardar un JWT en `localStorage` es una práctica común mal recomendada: al ser accesible desde JavaScript, cualquier vulnerabilidad de *Cross-Site Scripting* (XSS) en el frontend permite a un atacante leer el token completo y suplantar al usuario sin límite de tiempo hasta que expire. Por eso, en el backend de BioPet (`JwtCookieService`), el token se entrega dentro de una cookie llamada `BIOPET_ACCESS_TOKEN`, construida con los atributos `HttpOnly`, `path=/` y `SameSite=Lax`, y con el atributo `Secure` activado según el entorno mediante la propiedad `security.jwt.cookie.secure`. De este modo, el navegador adjunta el token automáticamente en cada petición, pero ningún script del lado del cliente puede leerlo ni exfiltrarlo, siguiendo la recomendación de OWASP de proteger la confidencialidad de los identificadores de sesión mediante `HttpOnly` (OWASP, s.f.).

## 6. Los atributos HttpOnly, Secure y SameSite

- **HttpOnly:** impide que JavaScript acceda a la cookie mediante `document.cookie`, mitigando el robo del token ante un ataque XSS.
- **Secure:** obliga a que la cookie solo se transmita sobre HTTPS, evitando su exposición en una red no cifrada.
- **SameSite:** controla si la cookie se envía en peticiones de origen cruzado. El valor `Lax`, usado en BioPet, evita que la cookie viaje en peticiones cross-site iniciadas por scripts o formularios de terceros, reduciendo la superficie de ataque de *Cross-Site Request Forgery* (CSRF), aunque sigue permitiendo la navegación normal desde enlaces externos (OWASP, s.f.).

Ningún atributo por sí solo resuelve ambos problemas: `HttpOnly` protege la confidencialidad frente a XSS, mientras que `SameSite` (o un token anti-CSRF adicional) protege frente a CSRF; usarlos en conjunto es lo que reduce ambos riesgos simultáneamente.

## 7. Sticky sessions y el rol de Redis

Cuando el estado de sesión se guarda en la memoria de una instancia específica del backend, el balanceador de carga necesita *sticky sessions*: debe enrutar siempre al mismo usuario hacia la misma instancia, porque solo esa instancia tiene su sesión en memoria. Esto rompe la promesa de escalabilidad horizontal sin estado, ya que si esa instancia cae, la sesión se pierde, y el balanceador pierde libertad para repartir la carga de forma uniforme. Redis resuelve este problema centralizando la información temporal en un almacén externo, accesible por cualquier instancia del backend por igual, de modo que ninguna réplica necesita "recordar" nada por sí sola.

En BioPet, Redis no almacena una sesión de servidor tradicional —el proyecto es *stateless* por diseño, ya que la identidad viaja en el JWT—, pero sí cumple ese mismo rol de coordinación compartida en dos frentes: el `TokenBlacklistService` guarda en Redis los identificadores (`jti`) de los tokens invalidados por logout, bajo la clave `jwt:blacklist:{jti}` con un tiempo de expiración (TTL) igual al tiempo de vida restante del token; y la caché de listados de mascotas (`spring.cache.type=redis`) evita repetir consultas idénticas contra PostgreSQL. En ambos casos, cualquier instancia del backend puede consultar el mismo dato compartido sin depender de afinidad de sesión.

## 8. Sesiones en archivos, en base de datos relacional y en Redis

| Criterio | Archivos en disco | Base de datos relacional | Redis |
|---|---|---|---|
| Velocidad de lectura/escritura | Baja (I/O de disco, sin índices) | Media (depende de índices y carga transaccional) | Alta (estructuras en memoria) |
| Persistencia ante reinicio | Alta si el disco es persistente | Alta, con garantías ACID | Configurable; por defecto prioriza velocidad sobre durabilidad total |
| Atomicidad de operaciones | Baja, requiere bloqueos manuales | Alta, transacciones ACID nativas | Alta a nivel de comando individual, con soporte de operaciones atómicas simples |
| Escalabilidad entre instancias | Muy baja, el archivo vive en un solo disco/servidor | Buena, pero cada consulta compite con la carga transaccional del negocio | Muy buena, diseñado para acceso concurrente de múltiples clientes |

Redis resulta preferible para datos temporales y de acceso frecuente (blacklist de tokens, caché) precisamente porque combina la velocidad de una estructura en memoria con la posibilidad de ser consultado por cualquier instancia del backend, sin competir con las consultas transaccionales de negocio que sí pertenecen a PostgreSQL.

## 9. Los objetos Request y Response en Spring Boot

En Spring Boot, cada método de un `@RestController` recibe implícitamente la petición HTTP (a través de anotaciones como `@RequestBody`, `@PathVariable`, `@RequestParam` o `@CookieValue`, que extraen partes concretas del objeto `HttpServletRequest`) y produce una respuesta serializada automáticamente a JSON, encapsulada opcionalmente en un `ResponseEntity` para controlar el código de estado y los encabezados. Un ejemplo real y simplificado del proyecto es el endpoint de login de `AuthController`:

```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletResponse response) {
    AuthService.LoginResult result = authService.login(request);
    response.addHeader(HttpHeaders.SET_COOKIE, jwtCookieService.build(result.jwt()).toString());
    return ResponseEntity.ok(result.body());
}
```

Aquí, `@RequestBody LoginRequest request` deserializa el cuerpo JSON de la petición (correo y contraseña) en un objeto Java validado; `HttpServletResponse response` permite añadir manualmente el encabezado `Set-Cookie` con el JWT construido por `JwtCookieService`; y el `ResponseEntity<AuthResponse>` devuelto controla explícitamente que la respuesta se envíe con código `200 OK` junto con el cuerpo `AuthResponse` serializado a JSON.

## 10. Recomendación para el PFC

Para BioPet, la combinación adecuada es la que ya está implementada: **JWT como mecanismo de autenticación, transportado en una cookie `HttpOnly`, `Secure` y `SameSite=Lax`, con el identificador único del token (`jti`) registrado en una lista negra temporal en Redis al momento del logout**. Esto evita exponer el token a scripts del lado del cliente, reduce el riesgo de CSRF, mantiene el backend sin estado para escalar horizontalmente sin sticky sessions, y resuelve el único inconveniente real de JWT —que un token robado sigue siendo válido hasta su expiración— mediante la revocación explícita respaldada por el TTL de Redis.

## Referencias

Fielding, R. T. (2000). *Architectural styles and the design of network-based software architectures* [Tesis doctoral, University of California, Irvine]. https://ics.uci.edu/~fielding/pubs/dissertation/top.htm

Jones, M., Bradley, J., & Sakimura, N. (2015). *JSON Web Token (JWT)* (RFC 7519). Internet Engineering Task Force. https://www.rfc-editor.org/rfc/rfc7519

OWASP. (s.f.). *Session management cheat sheet*. OWASP Cheat Sheet Series. https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html

## Lo que falta actualizar cuando Jaime confirme evidencia final

- Adjuntar la captura o el resultado real de `scripts/verificar-logout.ps1` (login → 200, `/api/auth/me` con cookie válida → 200, logout → 204, `/api/auth/me` reintentado con el token anterior → 401).
- Confirmar el valor final de `security.jwt.expiration-ms` y `security.jwt.cookie.secure` en el entorno de despliegue (actualmente `3600000` ms y dependiente de la variable `JWT_COOKIE_SECURE`).
