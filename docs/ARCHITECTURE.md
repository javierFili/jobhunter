# JobHunter AI — Arquitectura

> Estado: propuesta de la Fase 0 (aprobada). Versiones exactas de las bibliotecas: ver
> `DEVELOPMENT_SETUP.md` §2.

## 1. Estilo: monolito modular

Una aplicación Spring Boot, un esquema MySQL, una SPA en React. Los módulos son paquetes de primer
nivel bajo `com.jobhunter` con APIs públicas explícitas. Sin microservicios, sin broker de mensajes,
sin Docker.

```
┌──────────────────────────── Browser ─────────────────────────────┐
│ React + TS SPA (Vite build)  ── JWT ──►  /api/v1/**              │
└──────────────────────────────────────────────────────────────────┘
                               │ HTTPS (nginx reverse proxy in prod)
┌──────────────────────────────▼───────────────────────────────────┐
│ Spring Boot (single JVM, port 8095)                              │
│                                                                  │
│  web layer (controllers, DTOs, validation, exception handler)    │
│  ─────────────────────────────────────────────────────────────── │
│  application services (use cases, @Transactional boundaries)     │
│  ─────────────────────────────────────────────────────────────── │
│  domain (entities, domain services, rules: matching, dedupe,     │
│          eligibility, factuality validator, state machines)      │
│  ─────────────────────────────────────────────────────────────── │
│  infrastructure (JPA repos, connectors, AI providers,            │
│                  document extraction, PDF rendering, crypto)     │
│                                                                  │
│  bounded executors: discovery pool, AI pool                      │
│  scheduler: DB-driven dispatcher (1 tick/min) + ShedLock         │
└───────┬───────────────────────────┬──────────────────────────────┘
        │ JDBC (HikariCP)           │ WebClient (timeouts, retry, CB, rate limit)
┌───────▼────────┐   ┌──────────────▼────────────────────────────────┐
│ MySQL 8.x      │   │ Himalayas · Remotive · Jobicy · RemoteOK       │
│ schema jobhunter│  │ Greenhouse · Lever · Ashby · SerpApi · Adzuna  │
│                │   │ Apify(off) · DeepSeek · Claude                 │
│                │   │ (each: on/off + credentials)                   │
└────────────────┘   └───────────────────────────────────────────────┘
        │
  local file storage: /var/lib/jobhunter/files (uploads, generated PDFs)
```

## 2. Módulos del backend

Raíz de paquetes: `com.jobhunter`. Cada módulo sigue la misma estructura interna:

```
<module>/
  api/            # controllers + request/response DTOs (records) + mappers
  application/    # use-case services (transaction boundaries), module's public facade
  domain/         # JPA entities, enums, domain services, domain events
  infrastructure/ # repositories, external adapters
```

Regla: un módulo puede llamar a otro solo a través de su fachada `application` (o escuchando sus
eventos de dominio). Las entidades no se comparten entre módulos; las referencias entre módulos se
hacen por ID. Se impone con pruebas ArchUnit.

| Módulo | Responsabilidad | Tipos clave |
|--------|-----------------|-------------|
| `shared` | Entidad base (auditoría, borrado lógico), modelo de errores, paginación, reloj, criptografía (`SecretCipher`), fábrica de clientes HTTP seguros contra SSRF, registro de límites de tasa/circuit breakers, utilidades JSON | `BaseEntity`, `ApiError`, `SafeUrlPolicy` |
| `auth` | Registro, inicio de sesión, emisión/validación de JWT, tokens de refresco, configuración de seguridad | `JwtService`, `RefreshToken` |
| `users` | Cuenta de usuario, roles, consentimientos, exportación/eliminación de datos | `User`, `UserConsent` |
| `profiles` | Perfiles profesionales, preferencias, enlaces personales. También aloja `experiences`, `education`, `skills`, `languages`, `certifications`, `projects` como subpaquetes (mismo dueño del agregado; evita 6 micromódulos) | `ProfessionalProfile`, `Experience`, `Project`, `UserSkill`, `ProfileSnapshotService` |
| `resumes` | Cargas, extracción, análisis, documentos y versiones de currículum, plantillas, renderizado a PDF | `ResumeExtractor`, `ResumeParser`, `ResumeVersion`, `PdfRenderer` |
| `optimizer` | Optimización del CV por oferta, validador de veracidad, comparación | `ResumeOptimizer`, `FactualityValidator` |
| `coverletters` | Generación y versiones de cartas de presentación | `CoverLetterService` |
| `jobsources` | SPI de conectores + implementaciones, registro de Actors de Apify, ejecuciones de fuentes | `JobSourceConnector`, `ApifyClient`, `ActorDefinition` |
| `jobs` | Catálogo normalizado de ofertas, referencias a fuentes, deduplicación, clasificación de elegibilidad/remoto, verificación de vencimiento, consultas de búsqueda | `JobOffer`, `JobSourceReference`, `DeduplicationService`, `EligibilityClassifier` |
| `searches` | Búsquedas guardadas, ejecuciones, programaciones, detección de nuevas ofertas | `SavedSearch`, `SearchExecution`, `SearchOrchestrator` |
| `ai` | SPI `AiProvider`, proveedor DeepSeek (WebClient), proveedor Claude (SDK oficial de Anthropic para Java), enrutamiento por tarea elegible por el usuario con respaldo, registro de prompts, validación de esquemas, seguimiento de uso, caché | `AiProvider`, `ClaudeAiProvider`, `AiTaskRouter`, `PromptRegistry`, `AiAnalysis` |
| `matching` | Puntuador determinista + puntuador semántico con IA, clasificación, explicaciones | `MatchScorer`, `JobMatch` |
| `applications` | Pipeline de postulaciones, máquina de estados, eventos, documentos, preparación del paquete de postulación, "Marcar como enviada" | `Application`, `ApplicationStateMachine` |
| `notifications` | Notificaciones dentro de la aplicación | `Notification` |
| `analytics` | Consultas agregadas de solo lectura para el dashboard y la información del mercado | `AnalyticsQueryService` |
| `scheduler` | Despachador de tareas basado en BD, ShedLock, ejecutores acotados | `ScheduleDispatcher` |
| `admin` | Configuración de integraciones, credenciales, límites, endpoints de salud/prueba, visor del log de auditoría | `IntegrationSetting`, `IntegrationTester` |

> No hay módulo de correo: el envío de correos se descartó del alcance por decisión del usuario
> (2026-11-26).

> La especificación §3 lista `experiences`, `education`, `skills`, `languages` como módulos
> separados. Se mantienen como **subpaquetes de `profiles`** porque comparten dueño y ciclo de vida;
> separarlos agregaría fachadas sin agregar aislamiento. Con el mismo razonamiento, `job-search` y
> `job-discovery` se fusionan en `searches` + `jobs`.

### 2.1 Abstracciones clave

```java
public interface JobSourceConnector {
    SourceType type();                                  // REMOTIVE, GREENHOUSE, ADZUNA, APIFY, ...
    ConnectorCapabilities capabilities();               // supportsKeyword, supportsCountry, supportsSalary, ...
    FetchResult fetch(SourceQuery query, FetchBudget budget); // budget: maxResults, maxCostUsd, deadline
    Optional<JobStillOpen> verify(JobSourceReference ref);    // expiry check, if supported
}

public interface AiProvider {
    String id();                                        // "deepseek", "openai"
    AiResponse complete(AiRequest request);             // messages, json schema, maxTokens, temperature, timeout
    ProviderPricing pricing(String model);
}

public interface ResumeExtractor { ExtractedDocument extract(StoredFile file); } // PDF, DOCX
public interface PdfRenderer     { byte[] render(String templateId, ResumeDocument doc, Locale locale); }
```

### 2.2 Flujos principales

**Ejecución de descubrimiento (programada o manual)**

1. `ScheduleDispatcher` (cada minuto, ShedLock) selecciona las `saved_searches` que corresponden
   (`next_run_at <= now`, activadas) hasta `max_concurrent_searches`, y fija `next_run_at` de forma
   atómica.
2. `SearchOrchestrator` convierte la búsqueda guardada en un `SourceQuery` por cada fuente activada,
   verifica los presupuestos (topes diarios/mensuales de solicitudes, tope en USD de Apify) y llama
   a los conectores en el ejecutor acotado de descubrimiento.
3. Cada conector devuelve `JobOfferDraft`s → `JobIngestionService`:
   a. sanea el HTML (lista de permitidos), calcula `content_hash`, canoniza las URLs;
   b. **deduplica** (ver `DATABASE.md` §4): coincide con `job_offers` existentes o crea una;
   c. hace upsert de `job_source_references` (una por fuente/id externo), actualiza `last_seen_at`;
   d. clasificación determinista: alcance remoto, elegibilidad para Bolivia, tipo de vinculación,
      normalización del salario, etiquetado de tecnologías (basado en diccionario).
4. El resumen de la ejecución se guarda en `search_executions` (encontradas, nuevas, duplicadas,
   errores, costo).
5. Ofertas nuevas para esta búsqueda → `search_execution_jobs` + notificación.
6. Opcional: encolar el análisis con IA **solo** para ofertas nuevas, no duplicadas, que pasen un
   prefiltro determinista (elegibilidad ≠ NOT_ELIGIBLE, coincidencia de tecnologías > umbral).

**Matching**

`DeterministicScorer` (ubicación, idioma, salario, contrato, palabras clave de seniority,
coincidencia de habilidades vía diccionario) → si no es concluyente o el usuario lo pide →
`AiJobAnalyzer` (en caché) → `MatchScorer` combina → fila en `job_matches` con puntuaciones por
dimensión, motivos y clasificación.

**Optimización del CV**

`ProfileSnapshotService` produce un JSON inmutable de hechos verificados (con IDs) → prompt con los
requisitos de la oferta → la IA (proveedor y modelo según la ruta elegida por el usuario para
`CV_OPTIMIZE`) devuelve un JSON de currículum estructurado donde cada viñeta referencia IDs de
hechos o es `SUGGESTED` → `FactualityValidator` verifica entidades, fechas, tecnologías y niveles
contra la instantánea → nueva `resume_version` (estado DRAFT) → el usuario edita/aprueba → PDF.

**Preparación de la postulación (sin envío)**

`READY_TO_APPLY` → el usuario abre "Preparar" → el sistema arma el paquete: CV en PDF, carta de
presentación, texto del correo o mensaje listo para copiar (asunto, cuerpo, destinatario sugerido),
respuestas listas para copiar, lista de verificación y enlace a la oferta original → el usuario
envía la postulación **por su cuenta** (correo personal, formulario, portal) → pulsa "Marcar como
enviada" (`POST /applications/{id}/mark-submitted`) → `application_events(SUBMISSION_CONFIRMED)` +
transición a `APPLIED`. La aplicación nunca envía correos.

## 3. Aspectos transversales

| Aspecto | Enfoque |
|---------|---------|
| Transacciones | `@Transactional` a nivel de servicio de aplicación; llamadas HTTP externas **fuera** de las transacciones de BD. |
| Concurrencia | `ThreadPoolTaskExecutor`s dedicados (descubrimiento: 4, IA: 2, configurables) con colas acotadas y política `CallerRuns`/rechazo. Los hilos virtuales de Java 21 se usan solo para HTTP bloqueante dentro de esos pools acotados. |
| Resiliencia | Resilience4j (o los reintentos/límites de concurrencia integrados de Spring Framework 7, ver el documento de configuración): timeouts, reintento con backoff exponencial + jitter solo ante 429/5xx, circuit breaker por proveedor, limitador de tasa Bucket4j por proveedor. |
| Errores | `@RestControllerAdvice` → `ProblemDetail` RFC 9457 con `code`, `traceId`. |
| Validación | Jakarta Validation en los DTOs; invariantes de dominio en entidades/servicios. |
| Mapeo | MapStruct (en tiempo de compilación) o mappers escritos a mano para DTOs pequeños. |
| Auditoría | `created_at/updated_at/created_by` vía auditoría JPA; acciones relevantes para la seguridad en `audit_logs`. |
| Archivos | Sistema de archivos local bajo una raíz configurable, nombres UUID aleatorios, metadatos en la BD; nunca se sirven por ruta. |
| Configuración | `application.yml` + variables de entorno (`JOBHUNTER_*`); perfiles `dev`, `test`, `prod`. |
| Documentación de la API | springdoc OpenAPI en `/api/docs` (desactivado o solo para administradores en producción). |

## 4. Arquitectura del frontend

SPA en React + TypeScript + Vite dentro de `frontend/`. Carpetas por funcionalidad:

```
frontend/src/
  app/            # router, providers (QueryClient, Theme, Auth), layout (sidebar, topbar)
  components/ui/  # shadcn/ui generated components
  components/     # shared composites: DataTable, FilterBar, StatusBadge, ScoreBadge, EmptyState
  lib/            # api client (fetch wrapper w/ JWT refresh), query keys, zod helpers, formatters
  features/
    auth/          # login, register
    dashboard/     # KPIs, charts
    jobs/          # discover (search + filters), job detail, recommended
    searches/      # saved searches CRUD, executions history
    profile/       # profile editor, experiences, projects, skills, languages, preferences
    resumes/       # uploads, parse review, editor, versions, compare (diff), PDF preview
    coverletters/
    applications/  # kanban (dnd-kit), table, detail, prepare package / mark submitted
    analytics/
    settings/      # account, consents, export/delete, theme
    integrations/  # admin: integration settings, actors, usage, AI routing
```

- **Estado del servidor:** TanStack Query (claves de consulta por funcionalidad; las mutaciones
  invalidan).
- **Formularios:** React Hook Form + esquemas Zod que reflejan las restricciones de los DTOs del
  backend.
- **Rutas:** React Router con rutas protegidas y guarda de rol para las páginas de administración.
- **Autenticación:** token de acceso en memoria; token de refresco en una cookie
  `HttpOnly; Secure; SameSite=Strict` limitada a `/api/v1/auth/refresh` (evita el robo de tokens
  por XSS).
- **Tema:** modo oscuro/claro de Tailwind con la estrategia `class`, persistido por usuario.
- **Gráficos:** Recharts. **Íconos:** lucide-react. **Arrastrar y soltar:** dnd-kit.
- **Desarrollo:** el servidor de desarrollo de Vite en el puerto 5175 redirige `/api` →
  `http://localhost:8095`.
- **Producción:** `npm run build` → archivos estáticos servidos por nginx (o copiados a `static/`
  de Spring).

## 5. Despliegue (sin Docker)

- `jobhunter.jar` ejecutado por un servicio `systemd` con un usuario dedicado `jobhunter`.
- nginx: TLS (Let's Encrypt), sirve la SPA, redirige `/api`.
- MySQL local, usuario de la aplicación con privilegios mínimos sobre el esquema `jobhunter`.
- Secretos en `/etc/jobhunter/jobhunter.env` (propietario `root:jobhunter`, modo 640) cargados por `EnvironmentFile` de systemd.
- Respaldos: `mysqldump` + directorio de archivos vía cron, cifrados y rotados.

## 6. Decisiones de arquitectura (resumen de ADRs)

| ADR | Decisión | Alternativas descartadas |
|-----|----------|--------------------------|
| 001 | Monolito modular, paquetes por módulo, impuesto con ArchUnit | Microservicios (prohibidos; excesivos) |
| 002 | Planificador basado en BD (un despachador, filas con `next_run_at`) en lugar de un `@Scheduled` por búsqueda | Registros dinámicos en `TaskScheduler` (sin límite, se pierden al reiniciar) |
| 003 | Consulta periódica (polling) de las ejecuciones de Apify (webhooks opcionales más adelante) | Los webhooks requieren una URL HTTPS pública, que no existe en una laptop de desarrollo |
| 004 | Primero puntuación determinista, luego IA, con caché | Puntuación solo con IA (costo, opacidad) |
| 005 | Currículum = JSON estructurado + plantilla HTML → PDF | Editar el DOCX directamente (frágil, no compatible con ATS) |
| 006 | Esquema de pruebas MySQL local para las pruebas de integración | Testcontainers (requiere Docker), H2 (diferencias de dialecto con los scripts Flyway de MySQL) |
| 007 | Secretos: variables de entorno para las claves del sistema; cifrado AES-GCM en BD para las claves ingresadas por el usuario | Columnas de BD en texto plano |
| 008 | Sin envío de correos: la aplicación prepara el paquete de postulación y el usuario lo envía (decisión del usuario, 2026-11-26) | Envío por SMTP o Resend con aprobación explícita |
