# Changelog

Todos los cambios relevantes de este proyecto se documentan aquí. Formato basado en
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Agregado — Fases 5 a 7 (2026-11-27)
**Fase 5 — Optimización de CV y cartas**
- "Optimizar mi CV" por oferta (ruta de IA CV_OPTIMIZE, DeepSeek o Claude): el esqueleto del CV (empresas, cargos,
  fechas, educación, certificaciones, idiomas) se copia SIEMPRE del perfil verificado; la IA solo aporta titular,
  resumen, redacción/orden de viñetas y orden de habilidades.
- `FactChecker`: referencias inexistentes, proyectos usados como empleo, tecnologías no verificadas (o sin experiencia
  profesional dentro de un empleo), cifras que no aparecen en los hechos citados (comparación por número completo) y
  años de experiencia inflados → la frase queda `SUGGESTED` con una nota y bloquea la aprobación.
- Cartas de presentación en 5 formatos × 5 idiomas × 3 tonos, verificadas párrafo por párrafo, versionadas, aprobación,
  PDF; el nombre del usuario se agrega localmente (nunca se envía a la IA).

**Fase 6 — Postulaciones (sin envío de correos)**
- Pipeline de 11 estados con transiciones permitidas; "Postulada" solo vía "Marcar como enviada" (registra método,
  fecha, evento y auditoría; programa un seguimiento a 7 días).
- Kanban + tabla, historial de eventos, notas, CV/carta asociados, paquete "Preparar postulación" (CV, carta, texto de
  correo listo para copiar generado sin IA, lista de verificación por método, advertencias de elegibilidad).
- Recordatorios diarios de seguimiento (notificaciones internas).

**Fase 7 — Analítica y refinamiento**
- Resumen, desglose de ofertas por 8 dimensiones, línea de tiempo, embudo de postulaciones, demanda observada de
  tecnologías con "tu nivel", consumo diario por integración; recomendaciones de habilidades con IA claramente
  separadas de los datos observados.
- Exportación completa en ZIP y eliminación irreversible de la cuenta (archivos incluidos; logs anonimizados).
- Límites de peticiones por usuario (general 300/min, IA 20/min, ejecuciones de búsqueda 10/min); OpenAPI desactivado
  salvo `JOBHUNTER_API_DOCS=true`.
- Índices V11 según `EXPLAIN` (sin *filesort* en el catálogo ni en recomendados).
- Despliegue sin Docker: `deploy/jobhunter.service` (systemd endurecido), `deploy/nginx-jobhunter.conf` (TLS, CSP,
  HSTS), `scripts/backup.sh` (mysqldump + archivos cifrados con age, rotación 14 días).

### Corregido (encontrado al ejecutar contra MySQL real y la app real)
- Colecciones perezosas leídas fuera de transacción (ofertas, experiencias, proyectos, perfiles) → carga inmediata por lotes.
- Filtro por idioma requerido no coincidía con el formato canónico JSON de MySQL.
- Bloqueo al eliminar la cuenta (auditoría escrita en otra transacción sobre filas bloqueadas).
- SQL de demanda de habilidades mal concatenado (text block).
- Cifras "respaldadas" por subcadena ("2" dentro de "2022") en el verificador.
- `.env` con `&` sin comillas rompía `source .env`; contraseñas de desarrollo ajustadas a la política de MySQL.

### Agregado — Fases 1 a 4 (2026-11-26)
**Fase 1 — Base**
- Backend Spring Boot 4.2.0 (Java 21, Maven Wrapper 3.9.16), MySQL 8 + Flyway (V1–V3), puerto 8095.
- Autenticación: registro/inicio de sesión, JWT HS256 de 15 min, refresh token opaco en cookie `HttpOnly`
  (`SameSite=Strict`) con rotación y detección de reutilización, Argon2id, bloqueo tras 10 intentos, límite de
  peticiones por IP, primer usuario = ADMIN, auditoría.
- Perfil profesional completo: perfiles múltiples con selección/orden de elementos, experiencias, proyectos (tipo
  obligatorio; nunca cuentan como empleo), educación, certificaciones, catálogo de 202 tecnologías con alias,
  habilidades con nivel de evidencia y años profesionales **calculados desde fechas** (períodos solapados no se
  duplican), idiomas CEFR, preferencias laborales, verificación de elementos, consentimientos por proveedor de IA.
- Frontend React 19 + Vite 8 + Tailwind 4 + shadcn/ui en español: login/registro, layout, temas, Mi perfil, panel,
  configuración.

**Fase 2 — CV**
- Carga PDF/DOCX (≤5 MB; extensión + tipo declarado + bytes mágicos; DOCX sin macros, sin rutas raras, límite de
  descompresión; PDF cifrado o >20 páginas rechazado), extracción local con PDFBox/POI.
- Analizador heurístico de CV (secciones en ES/EN/IT/DE/FR/PT, rangos de fechas multilingües, contacto, idiomas y
  niveles) y diccionario de tecnologías sensible a símbolos (`C#`, `C++`, `.NET`; `Java` ≠ `JavaScript`).
- Importación revisada al perfil como **Sin verificar**; solo los hechos verificados llegan a un CV.
- Documento de CV estructurado con `sourceRefs` por viñeta, versiones inmutables, aprobación (bloqueada por frases
  `SUGGESTED`), 2 plantillas ATS (Thymeleaf → openhtmltopdf, fuente DejaVu incrustada), PDF en 5 idiomas, comparación.

**Fase 3 — Descubrimiento de empleos**
- Conectores: Himalayas, Remotive, Jobicy, Remote OK, Greenhouse, Lever, Ashby (lista de empresas seguidas), SerpApi
  Google Jobs, Adzuna y ejecutor genérico de Actors de Apify (desactivado).
- Cliente HTTP único con lista blanca de hosts, solo HTTPS, bloqueo de IPs privadas (SSRF), redirecciones revalidadas,
  reintentos solo en 429/5xx con `Retry-After`, límite de tasa y circuit breaker por integración.
- Ingesta: HTML saneado, clasificación determinista (modalidad, alcance remoto, **elegibilidad desde Bolivia con
  evidencia citada**, tipo de vínculo y de contrato, seniority, idiomas requeridos), salarios normalizados a USD/mes con
  tipos de cambio cargados por el admin, etiquetado de tecnologías, deduplicación (id de fuente → URL canónica →
  huella + SimHash) conservando cada referencia de fuente; ofertas retiradas detectadas en tableros ATS.
- Búsquedas guardadas (manual, intervalo ≥60 min, diaria), despachador por minuto con ShedLock, ejecución acotada
  (2 en paralelo), historial por fuente, detección de ofertas nuevas, notificaciones internas, vencimiento nocturno.
- Panel de Integraciones: interruptor, credenciales de solo escritura cifradas (AES-256-GCM), límites diario/mensual,
  presupuesto USD, prueba de conexión, uso del mes. Fuentes, Actors (requieren evaluación escrita para activarse) y
  tipos de cambio administrables.

**Fase 4 — Motor de IA**
- Proveedores DeepSeek (API HTTP, `thinking` on/off verificado) y Claude (SDK oficial `anthropic-java` 3.14.0, salida
  estructurada, `effort`, caché de prompt, *fallbacks* del servidor en Opus/Sonnet 5.5); **ruta por tarea editable**
  (proveedor, modelo, esfuerzo/pensamiento, respaldo, tokens máximos).
- Gateway: consentimiento por proveedor (datos personales) → caché → presupuesto → llamada → validación de esquema y
  restricciones → **una** reparación → reintentos acotados en errores transitorios → costo (con tarifa valle de
  DeepSeek) y uso registrados.
- Prompts versionados con esquema JSON; defensa contra inyección (contenido no confiable delimitado y neutralizado,
  `injectionSuspected`, sin herramientas con efectos).
- Análisis de ofertas con IA (requisitos obligatorios/deseables, idiomas, salario, ubicación); la elegibilidad solo
  cambia si estaba UNKNOWN **y** la cita de la IA aparece literalmente en la oferta.
- Matching: puntuación determinista por 7 dimensiones con razones en español + ajuste semántico opcional con IA (no
  puede compensar habilidades obligatorias faltantes); clasificaciones y recomendaciones; ofertas recomendadas;
  puntuación automática y gratuita tras cada búsqueda; evaluación con IA de las mejores bajo demanda.
- Estructuración opcional de CV con IA (sin correos/teléfonos/enlaces; requiere consentimiento "Procesar CV").

### Diferencias de implementación respecto al diseño de la Fase 0
- `scheduled_searches` no existe: la programación vive en `saved_searches`; se agregó `saved_search_seen_jobs` para
  detectar ofertas nuevas por búsqueda.
- Los prompts por defecto se siembran desde `resources/prompts/*.json` al arrancar (no desde Flyway).
- `job_matches` usa `profile_key` (0 = sin perfil) en lugar de `profile_id` nulo, para poder tener clave única.
- `ai_task_routes` agrega `thinking` (DeepSeek) y `effort` (Claude).
- Los salarios de Adzuna se guardan sin período (no documentado) y por eso no se convierten a USD/mes.
- El horario pico de DeepSeek no excluye feriados chinos (aproximación documentada).

### Cambiado — decisiones del usuario (2026-11-26)
- **Fuentes de la Fase 3 aprobadas:** Himalayas, Remotive, Jobicy, RemoteOK, Greenhouse, Lever,
  Ashby, SerpApi y Adzuna. Apify sigue **DESACTIVADO** por defecto (por ahora solo fuentes
  directas).
- **Enrutamiento de IA:** para cada tarea de IA (análisis de ofertas, matching, análisis de CV,
  generación/optimización de CV, cartas de presentación) el usuario elige DeepSeek **o** Claude
  desde la pantalla de administración "AI routing". Ambos proveedores son de primer nivel; las
  rutas iniciales se siembran con DeepSeek.
- **Envío de correos descartado del alcance.** Se eliminan el módulo de correo, las integraciones
  Resend y SMTP, Spring Mail, la tabla `email_logs`, la tabla `application_approvals`, los
  endpoints `/email/*`, `/applications/{id}/send-email`, `/applications/{id}/approvals` y
  `/webhooks/resend`, el requisito MAIL-01 y los costos de Resend. La aplicación **prepara** el
  paquete de postulación (CV en PDF, carta de presentación, texto del correo o mensaje listo para
  copiar, lista de verificación); el usuario lo envía por su cuenta y pulsa "Marcar como enviada"
  (`POST /applications/{id}/mark-submitted`), que registra un evento de postulación. También
  quedan fuera el resumen de notificaciones por correo y la verificación/restablecimiento de
  contraseña por correo (AUTH-06).
- **MySQL:** se mantiene 8.0.46. La 8.0 está fuera de soporte (EOL); el código sigue siendo
  compatible con 8.4 para una actualización futura.
- **Spring Boot:** se eligió 4.2.0 (se resuelve desde Maven Central y tiene el soporte OSS más
  largo, hasta 2027-12-31). 4.1.3 queda solo como plan de contingencia.
- **Repositorio:** se creó un repositorio git separado dentro de `jobhunter/`.
- **Idioma de la documentación:** español. Los identificadores de código siguen en inglés.
- **Puertos:** API 8095, servidor de desarrollo de Vite 5175.
- Fases 1–4 aprobadas.

### Agregado — Fase 0 (2026-11-26)
- Inspección del entorno de desarrollo (Ubuntu 22.04, Java 21.0.13, Maven 3.6.3, Node 22.23.5,
  MySQL 8.0.46).
- Investigación de la documentación oficial de más de 20 fuentes de empleo, Apify, SerpApi,
  DeepSeek, Resend (luego descartado junto con el envío de correos), Affinda y OpenAI.
- Verificación de versiones del stack (Spring Boot 4.2.0 / 4.1.3, springdoc 3.2.1, Flyway +
  flyway-mysql, React 19.3, Vite 8.4, Tailwind 4.3, TypeScript 6.0.3).
- Documentación: VISION, REQUIREMENTS, ARCHITECTURE, DATABASE, API, INTEGRATIONS, SECURITY,
  AI_DESIGN, COST_CONTROL, DEVELOPMENT_SETUP, TESTING, ROADMAP, PHASE0_REPORT.
- Todavía no hay código de la aplicación.

### Cambiado — Fase 0, decisiones del usuario (2026-11-26)
- Se agregó la **API de Claude (Anthropic)** como segundo proveedor de IA: SDK oficial de Java
  3.14.0, enrutamiento por tarea con respaldo, consentimiento y presupuesto por proveedor
  (`AI_DESIGN.md` §2.0, `INTEGRATIONS.md` §4b).
- Se agregó el panel de control de integraciones: interruptor + credenciales de solo escritura +
  prueba para DeepSeek, Claude, Apify, Adzuna, Remotive y SerpApi (`INTEGRATIONS.md` §0). Remotive
  no tiene credencial porque su API pública no usa clave.
- **Apify se entrega desactivado.** El descubrimiento usa solo fuentes directas hasta que se
  active Apify.
- Nueva tabla `ai_task_routes`; nuevos endpoints de administración `/admin/ai/routes`,
  `/admin/ai/models`.
