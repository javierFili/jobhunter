# JobHunter AI — Integraciones externas

> Fecha de investigación: **2026-11-26**. Las fuentes son documentación oficial, y algunos
> endpoints se probaron con llamadas en vivo sin autenticación.
> Leyenda: **[V]** verificado en la página oficial citada · **[T]** probado en vivo, GET sin
> autenticación · **[U]** sin verificar o requiere verificación manual (página bloqueada, detrás
> de un inicio de sesión, o la documentación no dice nada).
> Los precios y límites cambian. Volver a verificar cada fuente antes de activarla en producción.

## 0. Panel de control de integraciones (decisión del usuario 2026-11-26)

Cada integración se configura desde **Integraciones** (administración). Cada una tiene:
- un interruptor **Activada / Desactivada**;
- **credenciales de solo escritura**: la interfaz solo muestra "configurada · ••••last4";
- límites (por día, por mes, presupuesto mensual en USD);
- un botón **"Probar conexión"**;
- estado: último éxito, último error, uso.

Una integración desactivada nunca se llama, ni siquiera desde las búsquedas programadas.

| Integración | Código | Credenciales en el panel | Estado inicial | Llamada de prueba más barata |
|-------------|--------|--------------------------|----------------|------------------------------|
| **DeepSeek** (IA) | `DEEPSEEK` | Clave de API | Activada una vez que se configura la clave | `GET /user/balance` (no consume tokens) |
| **Claude / Anthropic** (IA) | `CLAUDE` | Clave de API (`x-api-key`) | Activada una vez que se configura la clave | `GET /v1/models` (no consume tokens) |
| **Apify** | `APIFY` | Token de API | **DESACTIVADA.** Por ahora, el descubrimiento busca solo en las fuentes directas ("en crudo") | `GET /v2/users/me/limits` |
| **Adzuna** | `ADZUNA` | `app_id` + `app_key` | Activada una vez que se configuran las credenciales | 1 búsqueda, `results_per_page=1` |
| **Remotive** | `REMOTIVE` | **Ninguna**: la API pública no usa clave (verificado). El panel solo muestra el interruptor. Solo se agregaría un campo de clave opcional si algún día se contrata la API privada de pago de Remotive. | Activada | `GET /api/remote-jobs/categories` |
| **SerpApi** (Google Jobs) | `SERPAPI` | Clave de API | Activada una vez que se configura la clave | `GET /account.json` (gratis, no consume cuota) |
| Himalayas, Jobicy, RemoteOK, Greenhouse, Lever, Ashby | (una por fuente) | Ninguna (públicas); Himalayas acepta una clave gratuita opcional | Activadas | 1 GET liviano |

El envío de correos (Resend / SMTP) se descartó del alcance por decisión del usuario el
2026-11-26, así que no hay integraciones de correo.

Qué significa en la práctica "Apify desactivado":
- El conector de Apify, el registro de Actors y el sondeador de ejecuciones existen en el código,
  pero `SearchOrchestrator` los omite.
- El editor de búsquedas guardadas muestra las fuentes de Actors en gris con el aviso "Apify está
  desactivado".
- Activar Apify requiere un token, y cada Actor necesita su propia evaluación y su propio
  interruptor. Ambos niveles (integración y Actor) deben estar activados para que un Actor se
  ejecute.

Las credenciales se resuelven en este orden:
1. Variable de entorno, si está definida. El panel muestra entonces "definida por el entorno" y no
   permite editarla.
2. Si no, el valor cifrado guardado desde el panel (AES-256-GCM, ver `SECURITY.md` §6).

## 0.1 Resumen y recomendación

Fuentes aprobadas para la Fase 3 (decisión del usuario 2026-11-26): Himalayas, Remotive, Jobicy,
RemoteOK, Greenhouse, Lever, Ashby, SerpApi y Adzuna. Apify sigue desactivado por defecto.

| Fuente | Tipo | Autenticación | Costo | Relevancia para Bolivia | Uso comercial / SaaS | Recomendación |
|--------|------|---------------|-------|-------------------------|----------------------|---------------|
| **Himalayas** | API pública de empleos remotos | Ninguna (clave gratuita opcional) | Gratis | **Alta**: las restricciones de país y zona horaria son campos estructurados | Se exige crédito + enlace; postular mediante su enlace; no pasar ofertas a otros portales | **Fase 3 — primer conector** |
| **Remotive** | API pública | Ninguna | Gratis (API privada de pago desde $5k/mes) | Alta (remoto mundial), pero la ubicación es texto libre | Se exige enlace de vuelta + crédito; **prohibido condicionar el acceso a las ofertas a un registro**; 24 h de retraso | **Fase 3** (uso personal; reevaluar para el SaaS) |
| **Greenhouse** | Portal ATS (por empresa) | Ninguna para GET | Gratis | Depende de la empresa | Términos no indicados en la página de la documentación [U] | **Fase 3**, con lista de empresas a vigilar |
| **Lever** | Portal ATS (por empresa) | Ninguna para GET | Gratis | Depende de la empresa | No indicado [U] | **Fase 3**, con lista a vigilar |
| **Ashby** | Portal ATS (por empresa) | Ninguna | Gratis | Depende de la empresa | No indicado [U] | **Fase 3**, con lista a vigilar |
| **SerpApi Google Jobs** | API de búsqueda | Clave de API | 250 búsquedas gratis/mes; desde $25/mes | Media: cobertura amplia, incluida LATAM por `gl`/`location` | API de pago con licencia | **Fase 3** (con tope de presupuesto) |
| **Apify** (ejecutor genérico) | Plataforma de Actors | Token | $5 de uso gratis/mes; Actors desde alrededor de $1–4 por 1k resultados | Depende del Actor | Los Actors son **creados por la comunidad**; los términos de LinkedIn e Indeed prohíben el scraping | **Infraestructura en la Fase 3, integración DESACTIVADA** (decisión del usuario): por ahora solo fuentes directas; activación por Actor tras la evaluación del administrador |
| **Adzuna** | API de agregador | app_id + app_key | Clave gratuita; **el uso comercial requiere licencia** tras una prueba de 14 días | **Baja**: sin Bolivia, **sin filtro de remoto**, descripciones solo en fragmento | Licencia obligatoria para productos con ingresos | **Fase 3, opcional** (uso personal: mercados US/GB/DE/ES/IT/MX/BR) |
| **Jooble** | API de agregador | Clave, solicitada por formulario | 100 solicitudes/día por clave de país | No está clara; `bo.jooble.org` no resuelve [T] | Términos no analizados [U] | **Cuando se conceda el acceso** |
| Jobicy | API pública de remotos | Ninguna | Gratis (clave de pago para las URLs originales del ATS) | Media (filtro `geo`) | Se exige crédito; marcar las ofertas cerradas | Fase 3 (barata de agregar) |
| RemoteOK | JSON público | Ninguna | Gratis | Media | Enlace de vuelta seguible + crédito; sin logo | Fase 3 |
| Arbeitnow | API pública | Ninguna | Gratis; **el uso comercial requiere una tarifa negociada** | Baja–media (sobre todo UE/DE; `visa_sponsorship`) | Tarifa para uso comercial | Opcional, para el enfoque en Europa/visas |
| Recruitee | Portal ATS (por empresa) | Ninguna | Gratis | Depende de la empresa | No indicado | Más adelante, con el mismo mecanismo de lista a vigilar |
| SmartRecruiters / Workable | ATS (por empresa) | La documentación se contradice [U] | — | — | [U] | Más adelante, tras verificar |
| We Work Remotely | RSS | Ninguna | — | Media | La página de términos devolvió 403 [U] | En espera hasta verificar los términos |
| The Muse | API | — | — | — | La documentación devolvió 403 [U] | En espera |
| USAJobs | API gubernamental | Clave | Gratis | **Ninguna** (empleos federales de EE. UU.; ciudadanos de EE. UU.) | — | No recomendada |

**Conclusión.** La mejor oferta legítima para un desarrollador remoto radicado en Bolivia proviene
de **Himalayas, Remotive, Jobicy y RemoteOK** (remote-first, con datos de elegibilidad), más los
**portales ATS de empresas objetivo** (Greenhouse, Lever, Ashby), más **SerpApi** para una
cobertura amplia de LATAM y Europa. Adzuna aporta poco para este usuario. LinkedIn, Indeed y
Glassdoor solo son accesibles mediante Actors de scraping, que los términos de esas plataformas
prohíben. Permanecen **desactivados por defecto**, y activar uno es una decisión explícita y
documentada del administrador.

**Advertencia de licencias para el futuro SaaS.** Remotive (sin condicionar a registro), Adzuna
(licencia) y Arbeitnow (tarifa) restringen el uso comercial. Las columnas `terms_notes` y
`attribution_required` de cada fuente, más una marca `commercial_use_allowed` en `job_sources`,
deben revisarse antes de abrir la aplicación a otros usuarios.

---

## 1. Fuentes de empleo — detalles

### 1.1 Himalayas [V] https://himalayas.app/docs/remote-jobs-api
- Listado: `GET https://himalayas.app/jobs/api` (paginación por cursor, como máximo 20 por
  solicitud).
- Búsqueda: `GET https://himalayas.app/jobs/api/search` con `q`, `country`, `worldwide`,
  `seniority`, `employment_type`, `timezone`, `sort` y `page`.
- Verificación abierta/cerrada: `GET /jobs/api/companies/:company/jobs/:slug`. Esto permite
  implementar `verify()`.
- Funciona sin clave [T]. Una clave Bearer gratuita opcional da 60 solicitudes/min y 10k/día.
- Campos: `minSalary`, `maxSalary`, `currency`, `locationRestrictions`, `timezoneRestrictions`,
  `pubDate`, `applicationLink` y `guid`.
- Términos: crédito visible a Himalayas con un enlace; **Postular debe usar `applicationLink`**; no
  pasar ofertas a otros portales de empleo, agregadores, Google Jobs ni LinkedIn.
- Mapeo: `locationRestrictions` → `remote_countries` / `remote_scope`; una lista vacía junto con
  `worldwide` → `WORLDWIDE`.

### 1.2 Remotive [V] https://github.com/remotive-com/remote-jobs-api · [T] https://remotive.com/api/remote-jobs
- `GET https://remotive.com/api/remote-jobs` con `category`, `company_name`, `search` y `limit`.
  **No** hay parámetros de ubicación, salario, fecha ni página. Endpoint de categorías:
  `/api/remote-jobs/categories`.
- Sin autenticación. **Indicación de tasa: se aconsejan como máximo 4 llamadas al día; más de 2 por
  minuto provoca un bloqueo.**
- Campos: `id`, `url`, `title`, `company_name`, `category`, `tags`, `job_type`,
  `publication_date`, `candidate_required_location` (texto libre), `salary` (texto libre) y
  `description` (HTML).
- Tamaño del feed el 2026-11-26: **16 ofertas**, así que es pequeño y tiene 24 h de retraso.
- Términos: enlazar a la URL de Remotive **y** nombrar a Remotive como fuente. No volver a publicar
  las ofertas en sitios de terceros. **Mostrar las ofertas para captar registros incumple sus
  términos**, lo cual importa para un SaaS público.
- Implementación: una descarga global, como máximo cada 6 h, compartida por todas las búsquedas
  guardadas. El filtrado se hace localmente.

### 1.3 Greenhouse Job Board API [V] https://docs.greenhouse.io/job-board.html · [T]
- `GET https://boards-api.greenhouse.io/v1/boards/{board_token}/jobs?content=true`
- `GET .../boards/{board_token}/jobs/{job_id}?pay_transparency=true`
- Sin autenticación para GET. Solo el envío de postulaciones (POST) usa Basic Auth, **y nunca lo
  usaremos** (P3).
- **Sin parámetros de palabra clave, ubicación, fecha ni paginación**: se devuelve el portal
  completo y se filtra localmente. **Sin búsqueda global**: hay que conocer el board token de cada
  empresa (la tabla `company_watchlist`).
- Campos: `id`, `title`, `updated_at`, `location.name`, `absolute_url`, `content` (HTML),
  `departments` y `offices`; una oferta individual agrega `company_name`, `first_published` y
  `application_deadline`. El salario viene de `pay_input_ranges`.
- Límites de tasa: "puede tener límite de tasa", sin cifras. Nos autolimitamos a 1 solicitud/s.
- Ventaja: el portal está completo, así que se puede detectar con exactitud cuándo se retira una
  oferta.

### 1.4 Lever Postings API [V] https://github.com/lever/postings-api · [T]
- `GET https://api.lever.co/v0/postings/{site}?mode=json&skip=&limit=`. La instancia de la UE es
  `api.eu.lever.co`. Una publicación individual es `/v0/postings/{site}/{id}`.
- Filtros: `location`, `commitment`, `team`, `department` y `level` (distinguen mayúsculas). No hay
  filtro por palabra clave.
- Campos: `text` (título), `categories`, `country`, `workplaceType`, `descriptionPlain`, `lists`,
  `hostedUrl`, `applyUrl`, `salaryRange` y `createdAt` (vistos en la respuesta probada, no en el
  README).
- Sin autenticación para GET. No hay límite de tasa documentado para GET. Autolimitarse a 1
  solicitud/s.

### 1.5 Ashby Public Job Posting API [V] https://developers.ashbyhq.com/docs/public-job-posting-api · [T]
- `GET https://api.ashbyhq.com/posting-api/job-board/{name}?includeCompensation=true`
- No hay endpoint de oferta individual; se usa `jobUrl` del listado. No hay parámetros de búsqueda.
- Campos: `id`, `title`, `location`, `secondaryLocations`, `isRemote`, `workplaceType`,
  `employmentType`, `descriptionPlain`, `publishedAt`, `jobUrl`, `applyUrl`, `compensation` e
  `isListed`. **Omitir `isListed=false`**, porque esas ofertas están ocultas a propósito.
- Límites de tasa y términos: no documentados [U]. Autolimitarse a 1 solicitud/s.

### 1.6 Adzuna [V] https://developer.adzuna.com/docs/search, /overview, /docs/terms_of_service
- `GET https://api.adzuna.com/v1/api/jobs/{country}/search/{page}?app_id=&app_key=`
- Parámetros:
  - Palabras clave: `what`, `what_and`, `what_or`, `what_exclude`, `title_only`
  - Ubicación: `where`, `distance`
  - Fecha: `max_days_old`
  - Salario: `salary_min`, `salary_max`
  - Contrato: `full_time`, `part_time`, `contract`, `permanent`
  - Otros: `company`, `sort_by`, `results_per_page`
- Países: gb, us, at, au, be, br, ca, ch, de, es, fr, in, it, mx, nl, nz, pl, sg, za.
  **Bolivia no está en la lista. No hay parámetro de remoto.**
- La respuesta devuelve **solo un fragmento de la descripción**, más `redirect_url` y
  `salary_is_predicted`. Los salarios predichos se mapean a `salary_is_estimated=true`.
- No hay endpoint de oferta individual.
- Límites: se muestran en la cuenta tras registrarse [U].
- Términos:
  - Mostrar una etiqueta "Adzuna" con un enlace (al menos 116×23 px).
  - Mostrar un ícono "Jobsworth" junto a los salarios predichos.
  - No revender los datos.
  - Eliminar los datos cuando termine el acceso.
  - **El uso en un producto que genera ingresos o leads requiere una licencia** (precio no
    publicado [U]).

### 1.7 Jooble [V] https://jooblehelpcenter.freshdesk.com/…/60001448238-rest-api-documentation
- `POST https://jooble.org/api/{api_key}` con un cuerpo JSON.
- Campos del cuerpo: `keywords` (obligatorio), `location` (obligatorio), `radius`, `salary`, `page`,
  `ResultOnPage` y `companysearch`. No hay filtro de remoto ni de fecha.
- La clave se solicita mediante un formulario en https://jooble.org/api/about. El proceso de
  aprobación es [U]. **Las claves son por dominio de país**: "una clave de jooble.org solo cubre
  ofertas de EE. UU.". 100 solicitudes/día por clave; 429 al superarlas.
- Bolivia: `bo.jooble.org` no resuelve [T]. La cobertura es [U].
- Cada oferta devuelve solo un `snippet`, con un `link` a Jooble.

### 1.8 Jobicy [V] https://jobicy.com/jobs-rss-feed · [T]
- `GET https://jobicy.com/api/v2/remote-jobs` con `count` (1–100), `geo`, `industry`, `tag` y
  `cursor`. Sin clave. 30 solicitudes/s por IP.
- Cubre solo los últimos 7 días, publicados con 3 h de retraso. Dar crédito a Jobicy y marcar las
  ofertas cerradas. Una clave de pago ($0.01 por URL) devuelve las URLs originales del ATS.

### 1.9 RemoteOK [V] (campo `legal` de https://remoteok.com/api)
- `GET https://remoteok.com/api`. Sin clave.
- Exige un enlace de vuelta **seguible** y nombrar a Remote OK como fuente. Sin logos sin permiso.
  No se indica límite de tasa; autolimitarse a 1 llamada cada 6 h.

### 1.10 Arbeitnow [V] https://www.arbeitnow.com/blog/job-board-api
- `GET https://www.arbeitnow.com/api/job-board-api`. Sin clave. Tiene los campos `remote` y
  `visa_sponsorship`.
- **El uso comercial requiere proponerles una tarifa mensual.**

---

## 1b. Fuentes agregadas el 2026-11-27 (evaluación de la lista del usuario)

Comprobado en vivo con peticiones reales. Regla: solo API o feed público; nada de *scraping* de HTML ni de
plataformas que lo prohíban (para eso existe Apify, desactivado).

| Plataforma | Estado | Cómo | Notas |
|---|---|---|---|
| **Get on Board** | ✅ Integrada (`getonboard`) | API pública `/api/v0/search/jobs` | La mejor fuente LATAM: salario USD/mes, seniority y modalidad. `remote_local` = remoto solo desde los países listados. `fully_remote` sin país queda «desconocida» hasta que la IA encuentre evidencia. |
| **We Work Remotely** | ✅ Integrada (`weworkremotely`) | RSS back-end + full-stack | «Anywhere in the World» es su valor por defecto: si el texto restringe («US Remote»), gana el texto. |
| **LaraJobs** | ✅ Integrada (`larajobs`) | RSS `/feed` | El feed no trae descripción; se resume con etiquetas/ubicación/salario. |
| **Freelancer.com** | ✅ Integrada (`freelancer`) | API pública de proyectos activos (sin token) | Son proyectos, no empleos. El presupuesto fijo NO se trata como salario; la tarifa por hora sí. |
| RemoteOK | ✅ Ya estaba | API (`?tag=`) | |
| Lemon.io / Arc.dev | ⚠️ Indirecto | Sus ofertas llegan vía Himalayas/Remotive/RemoteOK | Sin API pública propia. |
| Upwork | ❌ | RSS cerrado (403); API GraphQL solo con app aprobada (OAuth) | Requiere solicitar acceso de desarrollador a Upwork. |
| Workana | ❌ | Sin API; bloquea bots (403) | Solo vía Apify (y revisar términos). |
| Fiverr | ❌ No aplica | No publica ofertas: el freelancer publica *gigs* | Crear perfil manualmente. |
| Malt / Shakers / Manfred / Tecnoempleo | ❌ | Sin API pública; Tecnoempleo bloquea (403) | Manual o Apify. |
| Turing / Toptal / BairesDev / Encora / Globant | ❌ No aplica | Redes de talento: se postula una vez al *pool* | Si publican en Greenhouse/Lever/Ashby, seguirlas en «Empresas seguidas». |
| Torre.ai | ⏸ Pendiente | API de búsqueda no documentada (respondió 400) | Investigar el formato oficial antes de integrar. |
| Wellfound | ❌ | Sin API; anti-bot agresivo | Manual. |
| RemotoJob / RemotoList / RemoteJobs.lat | ❌ | Solo HTML, sin feed | Apify si se habilita. |
| LinkedIn | ❌ | Términos prohíben el *scraping* | Cobertura indirecta vía SerpApi (Google Jobs). |
| Discord / Slack / Telegram | ❌ | Requiere ser miembro y bots autorizados por cada grupo | Fuera de alcance. |

## 2. SerpApi — Google Jobs [V] https://serpapi.com/google-jobs-api

- `GET https://serpapi.com/search?engine=google_jobs&q=…&api_key=…`. La clave viaja como parámetro
  de la query, así que **hay que enmascarar las query strings en los logs**.
- Parámetros de ubicación: `location` **o** `uule` (no ambos), más `gl` (país), `hl` (idioma) y
  `lrad` (km).
- La paginación usa `next_page_token` (10 resultados por página). **`start` está discontinuado.**
- Los filtros usan `uds`, tomado de la sección `filters` de la respuesta. **`chips` y `ltype` están
  obsoletos.**
- Caché: los resultados se guardan en caché 1 h para parámetros idénticos, y **las búsquedas en
  caché no cuentan para la cuota**. `no_cache=true` omite la caché; nunca lo usamos.
- Respuesta: `jobs_results[]` con `title`, `company_name`, `location`, `via` (el portal original),
  `description`, `detected_extensions{posted_at, salary, schedule_type}`, `job_highlights`,
  **`apply_options[{title, link}]`**, `share_link` y `job_id`.
- `google_jobs_listing` **ya no devuelve opciones de postulación**, así que no lo llamamos.
- Endpoint de la cuenta: `GET https://serpapi.com/account.json?api_key=` (la documentación también
  muestra `/account`; probar ambos [U]). Es gratuito y devuelve `plan_searches_left` y
  `this_hour_searches`.
- Precios [V] https://serpapi.com/pricing:

| Plan | $/mes | Búsquedas/mes | Búsquedas/hora |
|------|-------|---------------|----------------|
| Free | 0 | 250 | 50 |
| Starter | 25 | 1,000 | 200 |
| Developer | 75 | 5,000 | 1,000 |
| Production | 150 | 15,000 | 3,000 |

- Las búsquedas con error o fallidas no se cuentan. No se publica precio por excedente.
- Notas de mapeo:
  - `via` → atribución de la fuente ("via LinkedIn").
  - Cada `apply_options[].link` → una URL candidata de `job_source_reference`, usada para
    deduplicar contra URLs de ATS.
  - `posted_at` es texto relativo ("3 days ago"). Se convierte a fecha en la ingesta y se marca como
    aproximada.
- Alternativas:
  - SearchApi.io: `engine=google_jobs` [V]; precios [U].
  - Scrapingdog: existe una API de Google Jobs; sus parámetros y costo en créditos son [U].

---

## 3. Apify [V] https://docs.apify.com/api/v2

### 3.1 API usada

| Propósito | Endpoint | Notas |
|-----------|----------|-------|
| Iniciar ejecución (asíncrona) | `POST /v2/actors/{actorId}/runs?timeout=&memory=&maxItems=&maxTotalChargeUsd=&webhooks=` | `actorId` = ID o `username~actor-name`. El cuerpo JSON es la entrada del Actor. Devuelve 201 con el Run. La ruta heredada `/v2/acts/` también funciona. |
| Estado de la ejecución | `GET /v2/actor-runs/{runId}?waitForFinish=60` | Estados: READY, RUNNING, SUCCEEDED, FAILED, TIMING-OUT, TIMED-OUT, ABORTING, ABORTED. Campos de costo: `usageTotalUsd`, `chargedEventCounts`, `pricingInfo.pricingModel` y `defaultDatasetId`. |
| Abortar | `POST /v2/actor-runs/{runId}/abort?gracefully=true` | Se usa cuando se supera el plazo. |
| Elementos del dataset | `GET /v2/datasets/{id}/items?offset=&limit=&clean=true&format=json&fields=` | Cabeceras de paginación `X-Apify-Pagination-*`. |
| Límites de la cuenta | `GET /v2/users/me/limits` | `maxMonthlyUsageUsd` y `current.monthlyUsageUsd`. Se verifica antes de cada ejecución. |
| Uso mensual | `GET /v2/users/me/usage/monthly` | Dashboard. |
| Ejecución síncrona | `POST /v2/actors/{id}/run-sync-get-dataset-items` | Límite estricto de 300 s (408). **No se usa**; se usa la asíncrona. |

- Autenticación: `Authorization: Bearer <token>`. Nunca se usa la forma `?token=`.
- Límites de tasa: 60 solicitudes/s por recurso y 250k/min en total; 429 significa retroceder desde
  500 ms con jitter.
- Webhooks: eventos `ACTOR.RUN.SUCCEEDED` y `FAILED`; se reintentan hasta 11 veces durante unas 32
  h; pueden entregarse más de una vez. **v1 usa polling** (ADR-003), porque una laptop de desarrollo
  no tiene URL pública. Los webhooks son una mejora opcional de la Fase 7 para entornos desplegados:
  un token secreto en la URL, un handler idempotente y polling como respaldo.
- Los datos de costo pueden ser preliminares justo después de que termina una ejecución. Volver a
  leer la ejecución después de unos 10 s antes de la contabilidad final del costo.

### 3.2 Precios [V] https://apify.com/pricing
- Plan gratuito: $5 de uso/mes, 5 ejecuciones concurrentes. Starter: $19/mes. Scale: $199/mes.
- Modelos de precio de los Actors: pago por uso, pago por resultado y pago por evento. El alquiler
  se retiró en 2026; la publicación del blog se encontró mediante fragmentos de búsqueda y no se
  descargó completa [U].
- **`maxTotalChargeUsd`** pone un tope a una ejecución. Al alcanzarlo, la plataforma aborta la
  ejecución y los cargos por encima del tope no se facturan. Es **por ejecución**, así que también
  aplicamos nuestro propio tope mensual y fijamos `maxMonthlyUsageUsd` a nivel de cuenta en la
  consola de Apify.
- `maxItems` limita los elementos *cobrados* de los Actors de pago por resultado. No limita la
  salida.

### 3.3 Actors candidatos (todos creados por la comunidad; no se encontró un Actor oficial `apify/` de empleos)

| Actor | Precio (desde) | Notas | Estado |
|-------|----------------|-------|--------|
| `curious_coder/linkedin-jobs-scraper` | $1.00 / 1k resultados (pago por evento) | Entradas: `keywords`, `location`, `geoId`, `datePosted`, `limitPerSource`. Páginas públicas, sin sesión. **El Acuerdo de Usuario de LinkedIn §8.2 prohíbe el scraping.** | Desactivado por defecto |
| `misceres/indeed-scraper` | $3.00 / 1k (el uso de la plataforma no se cobra aparte) | Entradas: `position`, `location`, `country`, `maxItemsPerSearch`. **Los términos de Indeed prohíben el scraping automatizado.** Calificación 3.0. | Desactivado por defecto |
| `orgupdate/google-jobs-scraper` | $2.00 / 1k | Sin relación con Google. Se superpone con SerpApi. | No es necesario si se usa SerpApi |
| Glassdoor (Actors multiportal) | alrededor de $3–4 / 1k [U] | No descargado; términos de Glassdoor no revisados | No evaluado |

**Política.** El registro de Actors es genérico, así que cualquier Actor se puede configurar desde
la interfaz de administración con una plantilla de entrada y un mapeo de campos. Antes de poder
activar un Actor, el administrador debe completar una evaluación: documentación, modelo de precio,
calidad de los datos según una ejecución de prueba, los términos de la plataforma y los datos
personales que recopila. La decisión de activar el scraping de LinkedIn o Indeed es **del
usuario**, y el riesgo frente a los términos de la plataforma (bloqueo de cuenta o IP, exposición
legal) se muestra en la interfaz. El sistema nunca entrega cookies ni credenciales de inicio de
sesión a un Actor, y nunca usa Actors que resuelven CAPTCHAs.

---

## 4. DeepSeek (IA) [V] https://api-docs.deepseek.com/

- `POST https://api.deepseek.com/chat/completions` con `Authorization: Bearer`. Es compatible con
  OpenAI.
- Modelos: **`deepseek-flash`** (V4.1-Flash) y **`deepseek-v4-pro`**; 1M de contexto, 384K de
  salida máxima, modo de razonamiento activado por defecto. El nombre heredado `deepseek-v4-flash`
  todavía se acepta. `deepseek-chat` y `deepseek-reasoner` **no se mencionan** en la documentación
  actual, así que no los usamos [U].
- Modo JSON, caché, errores, saldo: ver `AI_DESIGN.md` §2.1.
- Precios (USD por 1M de tokens) [V] https://api-docs.deepseek.com/quick_start/pricing:

| | Flash fuera de pico | Flash pico | Pro fuera de pico | Pro pico |
|---|---|---|---|---|
| Entrada, acierto de caché | 0.003 | 0.006 | 0.022 | 0.044 |
| Entrada, fallo de caché | 0.15 | 0.30 | 0.66 | 1.32 |
| Salida | 0.60 | 1.20 | 1.98 | 3.96 |

  Las horas pico son 01:00–04:00 y 06:00–10:00 UTC, de lunes a viernes.
- Límite de concurrencia por cuenta: Flash 2,500, Pro 500.
- **Privacidad:** la política de privacidad dice que los datos personales se almacenan y procesan
  en la RPC. El desarrollador debe obtener el consentimiento del usuario final (Términos de la Open
  Platform §3.3). La retención y el entrenamiento con datos de la API no se cubren explícitamente
  [U, revisión legal]. Consecuencias: una pantalla de consentimiento, eliminación de PII y la
  posibilidad de elegir Claude por tarea.

## 4b. API de Claude (Anthropic): segundo proveedor de IA (decisión del usuario 2026-11-26)

Fuente: la referencia oficial de la API de Claude de Anthropic, incluida con Claude Code (tabla de
modelos en caché del 2026-11-03). Maven Central se verificó el 2026-11-26. **Volver a verificar
los precios en https://platform.claude.com antes de la Fase 4.**

- **Endpoint:** `POST https://api.anthropic.com/v1/messages` (Messages API). La lista de modelos,
  `GET /v1/models`, se usa como llamada gratuita de "Probar conexión".
- **Cliente:** el SDK oficial de Java, `com.anthropic:anthropic-java` **3.14.0** (el más reciente en
  Maven Central, 2026-11-24). Anthropic recomienda su SDK en lugar de HTTP directo. El SDK aporta
  errores tipados, reintentos integrados (408/409/429/5xx), timeouts y salidas estructuradas
  tipadas. Es la única excepción justificada a "WebClient para HTTP externo": el SDK gestiona su
  propio cliente OkHttp.
  - **Nota de dependencias:** el SDK depende de **Jackson 2** (`com.fasterxml.jackson` 2.19.x).
    Spring Boot 4 usa Jackson 3 (`tools.jackson`) pero sigue gestionando Jackson 2 (2.21.8). Ambos
    están en paquetes distintos y pueden convivir. Una compilación y prueba de humo en la Fase 4 lo
    confirma [U].
- **Autenticación:** la clave de API se envía en la cabecera `x-api-key` (el SDK lo hace). Se
  configura desde el panel o con `JOBHUNTER_ANTHROPIC_API_KEY`.
- **Modelos actuales y precios de lista** (USD por 1M de tokens):

| Modelo | ID | Entrada | Salida | Notas |
|--------|----|--------:|-------:|-------|
| Claude Opus 5.5 | `claude-opus-5-5` | 4.00 | 20.00 | El predeterminado recomendado por Anthropic. El razonamiento siempre está activo; la profundidad se ajusta con `effort` (por defecto `medium`). 1M de contexto. |
| Claude Sonnet 5.5 | `claude-sonnet-5-5` | 2.00 | 10.00 | 1M de contexto |
| Claude Haiku 5.5 | `claude-haiku-5-5` | 0.10 | 0.50 | Para prompts ≤ 100K tokens ($0.50 / $2.50 por encima). 1M de contexto. |
| Claude Fable 5.2 | `claude-fable-5-2` | 8.00 | 40.00 | El más capaz; no es necesario para esta aplicación |

- **Salidas estructuradas:** `output_config.format` con un JSON schema. La API garantiza que la
  respuesta coincide con el esquema. Esto elimina la mayor parte del manejo de "JSON inválido" que
  necesita DeepSeek, aunque el validador local de esquemas se ejecuta igual.
- **Esfuerzo:** `output_config.effort` (`low` … `max`). `low` sirve para extracción y puntuación;
  `high` para la optimización del CV. En Opus 5.5 el razonamiento **no se puede desactivar**, así
  que `effort` es el único control.
- **Rechazos:** verificar `stop_reason == "refusal"` antes de leer el contenido. Las solicitudes a
  `claude-opus-5-5` / `claude-sonnet-5-5` incluirán respaldos del lado del servidor (beta
  `server-side-fallback-2026-07-01`, `fallbacks: "default"`) para que una solicitud rechazada pueda
  ser respondida por un modelo de respaldo.
- **Caché de prompts:** `cache_control` en el prompt de sistema estático y el esquema. La entrada en
  caché se cobra muy por debajo de la entrada normal (la lectura de caché de Opus 5.5 cuesta
  $0.20/1M). Esto replica la estructura de prompt "lo estático primero" ya diseñada para DeepSeek.
- **Message Batches API:** asíncrona, al **50% del precio**, con resultados en cualquier orden.
  Sirve para la re-puntuación nocturna y el análisis masivo de ofertas (optimización de las Fases
  4/7).
- **Errores** (excepciones tipadas en el SDK):
  - 400 solicitud inválida, 401 autenticación, 403 permiso, 404 no encontrado, 413 demasiado
    grande: no se reintentan.
  - 429 límite de tasa (respeta `retry-after`), 500/529 sobrecarga: el SDK los reintenta.
  - Nuestro circuit breaker y el control de presupuesto siguen envolviendo cada llamada.
- **Límites de tasa:** se definen por organización y nivel en la consola de Anthropic [U: verificar
  el nivel de la cuenta].
- **Manejo de datos:** a diferencia de DeepSeek, Anthropic es una empresa de EE. UU. Hay que leer
  sus términos comerciales y la retención de datos de las entradas de la API antes de redactar el
  texto de consentimiento [U: enlazar la política vigente en la Fase 4]. Claude es un proveedor de
  primer nivel: el usuario puede elegirlo para cualquier tarea, incluidas las que procesan **datos
  del CV y del perfil** (decisión 3 en `PHASE0_REPORT.md`).

## 5. OpenAI (opcional) [V] https://developers.openai.com/api/docs/guides/structured-outputs
- `https://api.openai.com/v1/responses` con Structured Outputs (`json_schema`, `strict`).
- Solo se implementa si se quiere un proveedor adicional fuera de la RPC para los datos del CV. El
  modelo se elige al momento de implementarlo.

## 6. Affinda Resume Parser (opcional) [V] https://resume-parser.affinda.com/docs/api-reference
- `POST {region}/v1/resumes/parse` (`file` multipart), `Authorization: Bearer aff_…`.
- Es síncrono y no se guarda nada después del análisis.
- Regiones: global (AU), US (`resume-parser.us1…`) y EU (`resume-parser.eu1…`). Las claves están
  ligadas a la región.
- Límites: 20 MB; PDF, DOCX y otros formatos; timeout de unos 30 s. Errores: 402 `no_credits`,
  422 `password_protected_document`.
- Precios [V] https://www.affinda.com/resume-parser/pricing/: **US$0.10 por documento** en
  autoservicio, con 1,000 documentos gratis en los primeros 14 días.
- Decisión: **opcional, desactivado por defecto**. La extracción local con PDFBox/POI más
  heurísticas (y el análisis con IA con consentimiento) es lo predeterminado. Affinda necesitaría
  su propia entrada de consentimiento (`RESUME_PARSER_EXTERNAL`).

## 7. Correo

El envío de correos se descartó del alcance por decisión del usuario (2026-11-26): la aplicación
prepara el paquete de postulación y el usuario lo envía por su cuenta.

---

## 8. Contrato de integración (todos los conectores)

Cada conector debe:
1. Declarar sus capacidades, para que los filtros no soportados se apliquen localmente y la
   interfaz muestre qué filtros respeta cada fuente.
2. Respetar un `FetchBudget`: máximo de solicitudes, máximo de resultados, costo máximo en USD y un
   plazo.
3. Usar el `SafeHttpClient` compartido:
   - lista de hosts permitidos por integración
   - timeouts (conexión 5 s, lectura 30 s; IA y Apify se configuran aparte)
   - reintento solo ante 429/5xx, respetando `Retry-After`
   - limitador de tasa y circuit breaker
4. Nunca registrar secretos en los logs. Las claves en query strings (SerpApi, Adzuna, ruta de
   Jooble) se enmascaran.
5. Guardar la URL original y el texto de atribución, y conservar `raw_payload` (recortado) durante
   90 días.
6. Registrar el uso en `api_usage_logs`.
7. Tener pruebas unitarias basadas en JSON de fixtures grabados (sin red).

## 9. Elementos que requieren verificación manual antes de la Fase 3

1. Cuota y precio de licencia de Adzuna (visibles tras el registro).
2. Proceso de aprobación de Jooble, cobertura de Bolivia y términos.
3. Términos de uso de Greenhouse, Lever y Ashby respecto a la agregación de datos públicos de sus
   portales.
4. El parámetro de DeepSeek que desactiva el modo de razonamiento, y si los nombres de modelo
   heredados siguen funcionando.
5. Retención y entrenamiento con datos de la API de DeepSeek (revisión legal), antes de cualquier
   uso multiusuario.
6. Ruta del endpoint de cuenta de SerpApi (`/account` vs `/account.json`).
7. Precio vigente de cada Actor de Apify en el momento de activarlo.
8. Términos de We Work Remotely, The Muse y Glassdoor.
