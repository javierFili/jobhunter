# JobHunter AI — Requisitos

> Estado: Fase 0 cerrada; Fases 1–4 aprobadas el 2026-11-26. Los IDs de requisito se referencian
> desde el roadmap, las pruebas y el changelog.
> Prioridad: **M** = obligatorio (v1), **S** = recomendable (v1 si hay tiempo), **C** = opcional
> (más adelante).
> Fase = fase del roadmap en la que se entrega (ver `ROADMAP.md`).

## 1. Requisitos funcionales

### 1.1 Autenticación y usuarios (Fase 1)

| ID | Requisito | Prio |
|----|-----------|------|
| AUTH-01 | Registro / inicio de sesión con correo + contraseña (hash Argon2id o BCrypt). | M |
| AUTH-02 | Token de acceso JWT (de corta duración) + token de refresco rotativo, guardado como hash en la BD. | M |
| AUTH-03 | Roles `USER`, `ADMIN`; endpoints de administración bajo `/api/v1/admin/**`. | M |
| AUTH-04 | Cerrar sesión = revocación del token de refresco. Limitación de intentos de inicio de sesión contra fuerza bruta. | M |
| AUTH-05 | Exportación de datos de la cuenta (JSON + ZIP con archivos) y eliminación de la cuenta. | M (Fase 7) |
| AUTH-06 | Verificación de correo / restablecimiento de contraseña por correo. **Fuera de alcance por ahora:** requiere envío de correos, que fue descartado el 2026-11-26. | C |

### 1.2 Perfil profesional (Fase 1)

| ID | Requisito | Prio |
|----|-----------|------|
| PRF-01 | Información personal, titular, resumen, enlaces (GitHub, LinkedIn, portafolio, sitio web). | M |
| PRF-02 | Múltiples *perfiles profesionales* por usuario (p. ej. "Java Backend", "Laravel Freelance") que seleccionan o sobrescriben el resumen, el titular, las habilidades destacadas y las experiencias. | M |
| PRF-03 | Experiencia laboral (empresa, cargo, fechas, ubicación, modalidad, logros, tecnologías). | M |
| PRF-04 | Proyectos con un `kind` obligatorio: `PERSONAL`, `ACADEMIC`, `FREELANCE_CLIENT`, `OPEN_SOURCE`. Los proyectos **nunca** se presentan como empleo. | M |
| PRF-05 | Educación y certificaciones (emisor, fechas, URL de la credencial). | M |
| PRF-06 | Habilidades de un catálogo compartido (categoría: lenguaje, framework, base de datos, nube, herramienta, metodología) con `evidence_level` por usuario: `PROFESSIONAL`, `PROJECT_ONLY`, `ACADEMIC`, `LEARNING`, más años de uso profesional (derivados de las experiencias vinculadas cuando sea posible). | M |
| PRF-07 | Idiomas con nivel MCER (A1–C2, nativo) y certificado opcional. | M |
| PRF-08 | Preferencias laborales: cargos objetivo, regiones/países, modalidades, tipos de contrato, salario mínimo + moneda + periodo, idiomas en los que está dispuesto a trabajar. Se usan en el matching. | M |
| PRF-09 | Cada dato del perfil tiene `verified=true` una vez que el usuario lo confirma (los datos importados de un CV empiezan sin verificar). | M |

### 1.3 Currículums (Fase 2)

| ID | Requisito | Prio |
|----|-----------|------|
| CV-01 | Carga de PDF/DOCX (≤ 5 MB, validación de MIME + bytes mágicos). | M |
| CV-02 | Extracción local de texto (PDFBox / POI); se guarda el texto en bruto. | M |
| CV-03 | Análisis por secciones (contacto, resumen, experiencia, educación, habilidades, proyectos, certificaciones, idiomas) — heurístico + IA opcional; Affinda opcional. | M / C |
| CV-04 | Pantalla de revisión para importar los datos analizados al perfil (sin verificar → verificado). | M |
| CV-05 | Documentos de currículum construidos a partir de los datos del perfil (JSON estructurado), editados en un editor de currículum y renderizados a PDF mediante plantillas HTML. | M |
| CV-06 | Versiones de currículum inmutables (cada edición u optimización = nueva versión, con versión padre). | M |
| CV-07 | Al menos 2 plantillas compatibles con ATS (una sola columna, sin tablas para maquetar). | M |

### 1.4 Descubrimiento y búsqueda de empleos (Fase 3)

| ID | Requisito | Prio |
|----|-----------|------|
| JOB-01 | Interfaz `JobSourceConnector`; cada conector normaliza a `JobOfferDraft`. | M |
| JOB-02 | Conectores v1 (lista aprobada el 2026-11-26): Himalayas, Remotive, Jobicy, RemoteOK, Greenhouse, Lever, Ashby (portales públicos), SerpApi Google Jobs, Adzuna. Ejecutor genérico de Actors de Apify implementado pero **desactivado por defecto** (por ahora solo fuentes directas). Jooble después de que se apruebe el acceso. | M |
| JOB-03 | Lista de empresas a vigilar para los conectores ATS (board token / nombre del sitio por empresa). | M |
| JOB-04 | Búsquedas guardadas con filtros (título, tecnologías, región, país, modalidad, tipo de contrato, rango salarial + moneda + periodo, nivel de experiencia, idioma, publicado desde, empresa, elegibilidad remota). | M |
| JOB-05 | Ejecución manual + ejecución programada (cron/intervalo por búsqueda guardada) con historial de ejecuciones persistido. | M |
| JOB-06 | Detección de nuevas oportunidades por búsqueda guardada (ofertas que esa búsqueda no había visto antes) + notificación. | M |
| JOB-07 | Deduplicación entre fuentes; las referencias a cada fuente se guardan por separado. | M |
| JOB-08 | `first_seen_at`, `last_seen_at`, `last_verified_at`; detección de vencimiento/retiro. | M |
| JOB-09 | Clasificación del alcance remoto: `WORLDWIDE`, `LATAM`, `AMERICAS`, `EUROPE`, `COUNTRY_RESTRICTED`, `TIMEZONE_RESTRICTED`, `UNKNOWN`; además `bolivia_eligibility`: `ELIGIBLE`, `LIKELY`, `NOT_ELIGIBLE`, `UNKNOWN`, cada uno con texto de evidencia. | M |
| JOB-10 | Tipo de vinculación: `EMPLOYEE_LOCAL`, `EMPLOYEE_EOR`, `CONTRACTOR`, `FREELANCE_PROJECT`, `SPONSORED_RELOCATION`, `UNKNOWN`. | M |
| JOB-11 | Límites de tasa y de concurrencia por conector; planificador acotado. | M |

### 1.5 Motor de IA y matching (Fase 4)

| ID | Requisito | Prio |
|----|-----------|------|
| AI-01 | Abstracción `AiProvider`; `DeepSeekAiProvider` **y `ClaudeAiProvider`** (Anthropic, SDK oficial de Java), ambos proveedores de primer nivel; `OpenAiProvider` opcional. | M |
| AI-01b | Enrutamiento por tarea (proveedor + modelo + esfuerzo + respaldo) editable desde la pantalla de administración "AI routing". Para cada tarea de IA (análisis de ofertas, matching, análisis de CV, generación/optimización de CV, cartas de presentación) el usuario elige DeepSeek **o** Claude. Las rutas iniciales se siembran con DeepSeek. Cada proveedor se activa/desactiva de forma independiente, con su propia clave y su propio consentimiento. | M |
| AI-02 | Prompts versionados guardados en la BD (`ai_prompt_versions`), cada uno con su esquema JSON de salida. | M |
| AI-03 | Respuestas JSON estructuradas validadas contra el esquema; reintento acotado ante JSON inválido. | M |
| AI-04 | Uso de tokens + costo estimado por llamada (`api_usage_logs`). | M |
| AI-05 | Análisis de ofertas: requisitos (obligatorios/deseables), idiomas, salario, restricciones de ubicación, seniority. | M |
| AI-06 | Puntuación de coincidencia = dimensiones deterministas ponderadas + dimensiones semánticas de IA; clasificación `HIGH_MATCH`, `MEDIUM_MATCH`, `LOW_MATCH`, `NOT_ELIGIBLE`, `NEEDS_REVIEW`, con motivos por dimensión. | M |
| AI-07 | Caché por `(hash del contenido de la oferta, hash de la instantánea del perfil, versión del prompt)`. | M |
| AI-08 | Defensas contra inyección de prompts para contenido externo. | M |
| AI-09 | Nunca indica una probabilidad de contratación. | M |

### 1.6 Optimización de currículum y cartas de presentación (Fase 5)

| ID | Requisito | Prio |
|----|-----------|------|
| OPT-01 | "Optimizar mi CV" por oferta → nueva versión del currículum en el idioma elegido (ES/EN/IT/DE/FR). | M |
| OPT-02 | Cada viñeta generada lleva `source_refs` (IDs de entidades del perfil) o `status=SUGGESTED`, que requiere confirmación. | M |
| OPT-03 | El validador de veracidad rechaza empresas, fechas, certificaciones, tecnologías o niveles de idioma nuevos que no estén en el perfil. | M |
| OPT-04 | Comparación lado a lado Original vs Optimizado; edición; aprobación; exportación a PDF. | M |
| CL-01 | Cartas de presentación en 5 idiomas y 5 formatos (carta formal, mensaje corto, introducción por correo, propuesta freelance, mensaje de LinkedIn). Editables y versionadas. | M |

### 1.7 Postulaciones, sin envío de correos (Fase 6)

| ID | Requisito | Prio |
|----|-----------|------|
| APP-01 | Estados de postulación: DISCOVERED, ANALYZED, FAVORITE, CV_OPTIMIZED, READY_TO_APPLY, APPLIED, RESPONSE_RECEIVED, INTERVIEW, OFFER, REJECTED, WITHDRAWN, con una tabla de transiciones permitidas. | M |
| APP-02 | Guarda empresa, puesto, URL de la oferta, fuente, fecha, versión del currículum, carta de presentación, texto del correo o mensaje preparado, expectativa salarial, método, notas, fecha de seguimiento y eventos. | M |
| APP-03 | Vistas Kanban (arrastrar y soltar) y tabla. | M |
| APP-04 | Métodos: EMAIL, EXTERNAL_FORM, CAREER_PORTAL, LINKEDIN, FREELANCE_PLATFORM. Para todos los métodos, la aplicación **prepara** el paquete de postulación: CV en PDF, carta de presentación, texto del correo o mensaje listo para copiar (asunto, cuerpo, destinatario sugerido), respuestas listas para copiar, lista de verificación y enlace "abrir original". La aplicación no envía nada. | M |
| APP-05 | El usuario envía la postulación por su cuenta y luego pulsa "Marcar como enviada" (`POST /applications/{id}/mark-submitted`), lo que registra un evento de postulación (`SUBMISSION_CONFIRMED`) con fecha y hora y pasa la postulación a APPLIED. | M |

> El envío de correos (antes MAIL-01: SMTP y Resend) fue **descartado del alcance** por decisión del
> usuario el 2026-11-26.

### 1.8 Dashboard, analítica, administración (Fases 1, 7)

| ID | Requisito | Prio |
|----|-----------|------|
| DASH-01 | KPIs + gráficos listados en la especificación §14; filtros por rango de fechas/región/fuente. | M |
| DASH-02 | Demanda de tecnologías a partir de datos observados (conteos), claramente separada de las recomendaciones de IA. | M |
| ADM-01 | Configuración de integraciones: activar/desactivar, credenciales (solo escritura), límites, estado, último éxito/error, uso, costo estimado, "probar conexión". Integraciones que deben tener este panel: **DeepSeek, Claude, Apify, Adzuna, Remotive, SerpApi** (+ las fuentes gratuitas). Remotive no tiene campo de credencial porque su API pública no usa clave. | M |
| ADM-03 | **Apify empieza DESACTIVADO.** El descubrimiento usa solo fuentes directas hasta que el administrador active Apify (interruptor de la integración) *y* cada Actor individual (interruptor por Actor). | M |
| ADM-02 | Registro de Actors de Apify: id del actor, plantilla de entrada, mapeo, máximo de resultados, cargo máximo en USD, activado, notas de evaluación de términos. | M |
| NOTIF-01 | Notificaciones dentro de la aplicación. (El resumen por correo quedó fuera de alcance junto con el envío de correos.) | M |

## 2. Requisitos no funcionales

| ID | Requisito |
|----|-----------|
| NFR-01 | Se ejecuta de forma nativa en Ubuntu 22.04+ (Java 21, Node 22, MySQL 8.x). **Sin Docker** en ninguna parte (incluidas las pruebas). |
| NFR-02 | Monolito modular; los límites entre módulos se imponen mediante la estructura de paquetes + pruebas ArchUnit. |
| NFR-03 | p95 < 300 ms en endpoints CRUD/listado con 100k ofertas de empleo (consultas indexadas, paginación). |
| NFR-04 | Todos los secretos vía variables de entorno o columnas de BD cifradas con AES-GCM, con una clave maestra provista por el entorno; nunca se devuelven al frontend. |
| NFR-05 | Todas las llamadas HTTP externas: timeouts, reintentos acotados con backoff, circuit breaker, límite de tasa por proveedor, política de URLs segura contra SSRF. |
| NFR-06 | Controles de presupuesto mensual: el gasto en IA y en Apify se detiene en los topes configurados. |
| NFR-07 | Ninguna llamada real a APIs de pago en las pruebas automatizadas. |
| NFR-08 | Interfaz preparada para i18n (primero inglés; luego español). Documentos generados en ES/EN/IT/DE/FR. |
| NFR-09 | Accesibilidad: navegación por teclado, contraste WCAG AA en ambos temas. |
| NFR-10 | Observabilidad: logs estructurados, health de Actuator, página de estado de integraciones. |
