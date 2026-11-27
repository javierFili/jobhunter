# JobHunter AI — Seguridad y privacidad

> Modelo de amenazas y controles de la Fase 0. Se revisa de nuevo en la Fase 7 (auditoría de
> seguridad).

## 1. Activos y amenazas

| Activo | Amenazas | Controles principales |
|--------|----------|-----------------------|
| CV / perfil del usuario (datos personales) | Filtración vía API, proveedor de IA, logs, respaldos | Verificación de propiedad, consentimiento antes del procesamiento externo, eliminación de PII, sin PII en los logs, respaldos cifrados |
| Secretos de integraciones (DeepSeek, Claude, Apify, SerpApi, Adzuna) | Exposición al frontend/logs/volcado de la BD | Variables de entorno o columnas cifradas con AES-256-GCM; API de solo escritura; logs enmascarados |
| Servidor | SSRF mediante URLs ingresadas por el usuario (URLs de portales ATS, entradas de actors, enlaces de ofertas), cargas maliciosas | Política de URLs con lista de permitidos, validación de cargas, el servidor no descarga URLs arbitrarias |
| Pipeline de IA | Inyección de prompts en ofertas / CVs | Envoltura de contenido no confiable, salida validada por esquema, la IA no tiene herramientas con efectos secundarios |
| Presupuesto | Gasto descontrolado en APIs | Topes estrictos verificados antes de cada llamada de pago |

> La aplicación no envía correos (descartado por decisión del usuario el 2026-11-26), así que no
> guarda credenciales de correo ni existe riesgo de que se use para enviar spam.

## 2. Autenticación y autorización

- Contraseñas: **Argon2id** (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`), longitud
  mínima 10, verificadas contra una pequeña lista de contraseñas comunes.
- Token de acceso: JWT HS256, 15 min, claims `sub`, `role`, `iat`, `exp`, `jti`. Secreto tomado de
  `JOBHUNTER_JWT_SECRET` (≥ 256 bits).
- Token de refresco: valor opaco aleatorio de 256 bits, guardado como hash SHA-256, 14 días,
  **rotación con detección de reutilización** (reutilizar un token ya rotado revoca toda la
  familia). Se entrega como cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.
- Limitación de inicios de sesión: Bucket4j por IP + por cuenta; bloqueo de la cuenta por 15 min
  tras 10 fallos.
- Autorización: `ROLE_USER`, `ROLE_ADMIN`. **Todo** acceso a recursos del usuario pasa por métodos
  de repositorio que filtran por `user_id` (`findByIdAndUserId`) — devuelve 404, no 403, para evitar
  la enumeración. Las pruebas de integración comprueban que el acceso entre usuarios falla en cada
  endpoint.
- CSRF: no es necesario para la API con bearer token; el endpoint de refresco (basado en cookie)
  exige una cabecera personalizada (`X-Requested-With`) + `SameSite=Strict`.
- CORS: lista explícita de orígenes tomada de la configuración.

## 3. Validación de entradas

- Jakarta Validation en todos los DTOs (tamaños, patrones, enums); se rechazan las propiedades JSON
  desconocidas.
- Los filtros de las búsquedas guardadas se convierten a un DTO tipado antes de guardarse (sin pasar
  JSON en bruto).
- Paginación: `size ≤ 100`; campos de ordenamiento en lista de permitidos.
- El texto enriquecido del usuario (cartas de presentación, editor de currículum) se guarda como
  texto plano / markdown restringido y se renderiza escapado; el HTML para los PDFs se genera con
  Thymeleaf con escape (`th:text`).

## 4. Carga de archivos

- Máximo 5 MB (`spring.servlet.multipart.max-file-size`), un archivo por solicitud.
- Permitidos: PDF (bytes mágicos `%PDF-`), DOCX (bytes mágicos ZIP + `[Content_Types].xml` +
  `word/document.xml`). La extensión, el MIME declarado y los bytes mágicos deben coincidir.
- DOCX: protección contra bombas zip (`ZipSecureFile` de POI con ratio mínimo de inflado y tamaño
  máximo por entrada), sin macros (se rechaza `.docm`), se ignoran las relaciones externas.
- PDF: PDFBox con límite de memoria (`MemoryUsageSetting.setupTempFileOnly` / memoria principal
  máxima), límite de páginas (20), se rechazan los PDFs cifrados, sin ejecución de JavaScript
  (PDFBox no lo ejecuta).
- La extracción se ejecuta con un timeout en un ejecutor acotado.
- Se guardan fuera de la raíz web con nombres UUID; solo se descargan mediante un endpoint
  autorizado con `Content-Disposition: attachment` y `X-Content-Type-Options: nosniff`.

## 5. Protección contra SSRF

El servidor solo descarga:
1. URLs base fijas de los proveedores (en lista de permitidos por integración en el código, no
   editables para apuntar a hosts arbitrarios).
2. Endpoints de portales ATS construidos a partir de un **token validado**
   (`^[a-zA-Z0-9_-]{1,100}$`), nunca a partir de una URL ingresada por el usuario.
3. Verificación de "oferta todavía abierta": solo URLs en hosts de fuentes conocidas.

`SafeHttpClient` además: resuelve el DNS y rechaza rangos privados/loopback/link-local/multicast
(IPv4 + IPv6, incl. `169.254.169.254`), solo HTTPS, máximo 3 redirecciones, cada una revalidada,
tope de tamaño de respuesta (10 MB; los datasets de Apify se paginan), timeouts. Las entradas de los
Actors de Apify pueden contener URLs (URLs de inicio): se pasan a Apify, nuestro servidor no las
descarga.

## 6. Gestión de secretos

- Prioridad: variable de entorno → valor cifrado en la BD (`integration_settings.secret_ciphertext`).
- Cifrado: AES-256-GCM, IV aleatorio de 96 bits por valor, AAD = `integration_code|user_id`, clave
  tomada de `JOBHUNTER_MASTER_KEY`; `secret_key_version` permite la rotación.
- La API nunca devuelve secretos; solo `configured: true` y `last4`. La actualización es de solo
  escritura.
- Logs: un conversor de enmascaramiento de Logback para patrones conocidos (`api_key=`, `Bearer `,
  `app_key=`, `/api/{key}` para Jooble); logging de WebClient en DEBUG desactivado en producción.
- `.env` está en git-ignore; `.env.example` no contiene valores.

## 7. Específico de la IA (inyección de prompts y datos compartidos)

- El contenido externo se pasa como datos no confiables, delimitados y etiquetados; las
  instrucciones que contenga se ignoran por política y el modelo informa `injection_suspected`.
- El modelo no puede actuar: sin herramientas, no se descargan URLs de su salida, las salidas se
  validan por esquema y se restringen a enums. El texto generado para correos o mensajes se muestra
  al usuario, que lo revisa, lo copia y lo envía por su cuenta; la aplicación no envía nada.
- Consentimiento: `user_consents` por proveedor (`AI_PROCESS_PROFILE`, `AI_PROCESS_CV`,
  `RESUME_PARSER_EXTERNAL`). Sin consentimiento, las funciones de IA que necesitan datos personales
  quedan desactivadas (el análisis solo de la oferta sigue funcionando, ya que no contiene datos
  personales).
- Minimización: el nombre, correo, teléfono, dirección y enlaces se eliminan antes del envío y se
  vuelven a insertar localmente.
- Según su política de privacidad, DeepSeek almacena los datos en la RPC, y la pantalla de
  consentimiento lo indica.
- Claude (Anthropic, EE. UU.) tiene su propia entrada de consentimiento (`provider = claude`). Sus
  términos de retención de datos se enlazan en la pantalla de consentimiento, después de
  verificarlos en la Fase 4.
- El consentimiento se verifica por proveedor en el momento de la llamada. Cambiar la ruta (el
  usuario puede elegir DeepSeek o Claude por tarea) nunca omite el consentimiento.

## 8. Respeto a las plataformas (antiabuso)

- Sin resolución de CAPTCHA, sin automatizar inicios de sesión, sin reenviar credenciales/cookies a
  los Actors.
- Límites de tasa más estrictos que las indicaciones publicadas (p. ej. Remotive ≤ 4 llamadas/día).
- Atribución y enlace de vuelta según los términos de cada fuente; se conservan las URLs originales.
- "Postular" nunca envía nada automáticamente; la asistencia en el navegador = abrir la URL +
  respuestas listas para copiar.

## 9. Limitación de tasa de la API

Bucket4j en memoria por usuario: 120 sol./min en general, 10/min para endpoints que disparan IA,
5/min para "ejecutar búsqueda ahora", inicio de sesión 10/min por IP. 429 con `Retry-After`.

## 10. Derechos de privacidad

- **Exportación:** `POST /api/v1/me/export` → ZIP con JSON de todos los datos del usuario + archivos
  cargados/generados.
- **Eliminación:** `DELETE /api/v1/me` (con reconfirmación de contraseña) → borra físicamente las
  filas del usuario (cascadas de FK) y sus archivos; anonimiza `api_usage_logs`/`audit_logs`
  (`user_id = NULL`).
- Retención de datos: ver `DATABASE.md` §6.

## 11. Log de auditoría

Se registra: inicio de sesión exitoso/fallido, cambio de contraseña, cambio de rol, cambio de
configuración/secreto de integraciones, cambio de consentimiento, postulación marcada como enviada,
exportación, eliminación, acciones de administración.

## 12. Transporte y cabeceras

Producción detrás de nginx con TLS 1.2+; HSTS; `Content-Security-Policy` para la SPA
(`default-src 'self'`); `X-Frame-Options: DENY`; `Referrer-Policy: no-referrer` (evita filtrar URLs
internas al abrir enlaces externos de ofertas; los enlaces usan `rel="noopener noreferrer"`).

## 13. Higiene de dependencias

OWASP Dependency-Check (plugin de Maven) y `npm audit` en la Fase 7; versiones fijadas; Maven
wrapper con checksum.
