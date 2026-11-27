# JobHunter AI — Propuesta de API REST

> Propuesta de la Fase 0. Ruta base `/api/v1`. JSON (`application/json`), UTF-8. Autenticación:
> `Authorization: Bearer <access token>`, salvo los endpoints marcados como *public*. Una vez
> implementado, el documento OpenAPI generado por springdoc es la fuente de verdad.

## 1. Convenciones

- **Paginación:** `?page=0&size=20&sort=publishedAt,desc` → `{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`.
- **Errores:** Problem Details según RFC 9457:
  `{ "type": "https://jobhunter/errors/validation", "title": "...", "status": 400, "code": "VALIDATION_FAILED", "detail": "...", "traceId": "...", "errors": [{"field": "...", "message": "..."}] }`.
  Códigos comunes: `VALIDATION_FAILED`, `NOT_FOUND`, `CONFLICT`, `BUDGET_EXCEEDED`,
  `CONSENT_REQUIRED`, `INTEGRATION_DISABLED`, `RATE_LIMITED`.
- **Bloqueo optimista:** los recursos editables devuelven `version`; una `version` desactualizada
  produce 409.
- **Operaciones largas** (ejecución de búsqueda, análisis con IA, optimización del CV) devuelven
  `202 Accepted` y un cuerpo `{ "taskId", "status" }`. Los clientes consultan el recurso; más
  adelante podría agregarse un stream SSE.
- **Fechas:** ISO-8601, UTC.

## 2. Endpoints

### Autenticación (Fase 1)
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/auth/register` *public* | Crea una cuenta |
| POST | `/auth/login` *public* | Devuelve el token de acceso; establece la cookie de refresco |
| POST | `/auth/refresh` *cookie* | Rota el token de refresco y devuelve un nuevo token de acceso |
| POST | `/auth/logout` | Revoca la familia del token de refresco |
| GET | `/me` | Usuario actual |
| PATCH | `/me` | Nombre, idioma, zona horaria, tema |
| PUT | `/me/password` | Cambiar contraseña |
| GET / PUT | `/me/consents` | Listar o actualizar consentimientos (por proveedor) |
| POST | `/me/export` | Genera el ZIP de exportación (202) → `GET /me/export/{id}` para descargarlo |
| DELETE | `/me` | Eliminar la cuenta (cuerpo: contraseña) |

### Perfil (Fase 1)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET/POST | `/profiles` | Listar o crear perfiles profesionales |
| GET/PUT/DELETE | `/profiles/{id}` | Un perfil |
| PUT | `/profiles/{id}/items` | Seleccionar y ordenar las experiencias, proyectos y habilidades de este perfil |
| GET/POST | `/experiences` · GET/PUT/DELETE `/experiences/{id}` | Historial laboral |
| GET/POST | `/projects` · `/projects/{id}` | Proyectos, con un campo `kind` |
| GET/POST | `/educations` · `/educations/{id}` | Educación |
| GET/POST | `/certifications` · `/certifications/{id}` | Certificaciones |
| GET/PUT | `/skills` (habilidades del usuario, en bloque) · DELETE `/skills/{skillId}` | Habilidades con nivel de evidencia |
| GET | `/catalog/skills?q=` | Autocompletado desde el catálogo de habilidades |
| GET/PUT | `/languages` | Idiomas (en bloque) |
| GET/PUT | `/preferences` | Preferencias laborales |
| POST | `/profile/verify` | Marcar elementos importados como verificados (`{ "itemRefs": [...] }`) |

### Currículums (Fases 2, 5)
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/resumes/uploads` (multipart) | Cargar un PDF o DOCX → extracción (202) |
| GET | `/resumes/uploads/{id}` | Estado de la extracción, texto en bruto, secciones analizadas |
| POST | `/resumes/uploads/{id}/import` | Importar al perfil los elementos analizados seleccionados (sin verificar) |
| GET/POST | `/resumes` · GET/PATCH/DELETE `/resumes/{id}` | Documentos de currículum |
| GET | `/resumes/{id}/versions` · `/resumes/{id}/versions/{v}` | Historial de versiones |
| POST | `/resumes/{id}/versions` | Guardar una versión editada (nueva versión) |
| POST | `/resumes/{id}/versions/{v}/approve` | Aprobar una versión (bloqueado si hay errores de validación) |
| GET | `/resumes/{id}/versions/{v}/pdf` | Renderizar o descargar el PDF |
| GET | `/resumes/{id}/compare?from=&to=` | Comparación estructurada |
| POST | `/jobs/{jobId}/optimize-resume` | `{ resumeId, language, model? }` → 202 → nueva versión DRAFT |
| GET | `/resume-templates` | Plantillas disponibles |

### Ofertas y descubrimiento (Fase 3)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/jobs` | Buscar en el catálogo. Filtros: `q`, `skills`, `country`, `region`, `modality`, `remoteScope`, `eligibility`, `contractType`, `engagementType`, `salaryMinUsdMonth`, `salaryMax`, `currency`, `period`, `seniority`, `language`, `postedSince`, `company`, `source`, `classification`, `saved`, `hidden` |
| GET | `/jobs/{id}` | Detalle: fuentes, requisitos, coincidencia del usuario y estado de la postulación |
| PUT | `/jobs/{id}/state` | `{ saved, hidden }` |
| POST | `/jobs/{id}/verify` | Volver a verificar si la oferta sigue abierta (fuentes que lo permiten) |
| GET | `/jobs/recommended` | Ofertas ordenadas por coincidencia para el perfil activo |

### Búsquedas guardadas (Fase 3)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET/POST | `/searches` · GET/PUT/DELETE `/searches/{id}` | Configuraciones de búsqueda, incluida la programación |
| POST | `/searches/{id}/run` | Ejecución manual (202) |
| GET | `/searches/{id}/executions` · `/searches/executions/{execId}` | Historial; resultados por fuente |
| GET | `/searches/executions/{execId}/jobs?onlyNew=true` | Ofertas de una ejecución |
| GET | `/sources` | Fuentes activadas y sus capacidades (qué filtros respeta cada una) |

### Lista de empresas a vigilar (Fase 3)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET/POST | `/watchlist` · DELETE `/watchlist/{id}` | `{ ats: GREENHOUSE \| LEVER \| ASHBY, boardToken, companyName }` |
| POST | `/watchlist/validate` | Verifica que un token devuelva un portal (una solicitud) |

### IA y matching (Fase 4)
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/jobs/{id}/analyze` | Análisis de la oferta con IA (en caché) → 202 |
| POST | `/jobs/{id}/match?profileId=` | Calcular o actualizar la coincidencia |
| GET | `/jobs/{id}/match?profileId=` | Desglose de la coincidencia: dimensiones, motivos, fortalezas, brechas |
| POST | `/matches/recompute` | Recalcular coincidencias desactualizadas (solo determinista, salvo `ai=true`) |

### Cartas de presentación (Fase 5)
| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/jobs/{id}/cover-letters` | `{ format, language, profileId, tone? }` → 202 |
| GET | `/cover-letters?jobId=` · GET/PUT/DELETE `/cover-letters/{id}` | Editar (crea una nueva versión) |
| GET | `/cover-letters/{id}/pdf` | Descargar como PDF |

### Postulaciones, sin envío de correos (Fase 6)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/applications?view=kanban\|table&status=&q=` | Listar |
| POST | `/applications` | Desde una oferta (`jobOfferId`) o manual |
| GET/PATCH/DELETE | `/applications/{id}` | Campos, notas, fecha de seguimiento, expectativa salarial |
| POST | `/applications/{id}/transition` | `{ toStatus, note }`. Validado por la máquina de estados. |
| PUT | `/applications/kanban-order` | Reordenar después de arrastrar y soltar |
| GET/POST | `/applications/{id}/events` | Historial de comunicaciones |
| POST | `/applications/{id}/prepare` | Arma el paquete: CV en PDF, carta de presentación, texto del correo o mensaje listo para copiar, lista de verificación |
| POST | `/applications/{id}/mark-submitted` | El usuario confirma que envió la postulación por su cuenta. Registra un evento `SUBMISSION_CONFIRMED` y pasa el estado a `APPLIED`. |

> No hay endpoints de envío de correo (`/email/*`, `/applications/{id}/send-email`,
> `/webhooks/resend`): el envío de correos se descartó del alcance por decisión del usuario
> (2026-11-26).

### Notificaciones, dashboard, analítica (Fases 1, 7)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/notifications?unread=true` · POST `/notifications/{id}/read` · POST `/notifications/read-all` | Notificaciones |
| GET | `/dashboard/summary?from=&to=` | KPIs |
| GET | `/analytics/jobs/by-{country\|region\|source\|technology\|salary-band\|modality}` | Datos observados del mercado |
| GET | `/analytics/applications/funnel` | Postulada → respuesta → entrevista → oferta |
| GET | `/analytics/usage?from=&to=` | Tokens y costo de IA, costo de Apify, solicitudes a APIs |
| GET | `/analytics/skills/demand?region=` | Demanda observada de tecnologías |
| POST | `/analytics/skills/recommendations` | Recomendaciones de IA, etiquetadas como `AI_GENERATED` |

### Administración (rol ADMIN)
| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/admin/integrations` | Estado, uso, límites. **Sin secretos.** |
| PUT | `/admin/integrations/{code}` | Activar o desactivar, configuración, límites. `code` ∈ `DEEPSEEK`, `CLAUDE`, `APIFY`, `ADZUNA`, `REMOTIVE`, `SERPAPI`, … Activar una integración que necesita credenciales y no las tiene → 409 `CREDENTIALS_REQUIRED`. |
| DELETE | `/admin/integrations/{code}/secret` | Eliminar la credencial guardada (también desactiva la integración) |
| PUT | `/admin/integrations/{code}/secret` | Actualización del secreto, solo escritura |
| POST | `/admin/integrations/{code}/test` | Verificación de conectividad lo más barata posible (p. ej. endpoint de cuenta o saldo) |
| GET/POST/PUT/DELETE | `/admin/sources` · `/admin/sources/{id}` | Configuración de fuentes de empleo y notas de términos |
| GET/POST/PUT | `/admin/apify/actors` · `/admin/apify/actors/{id}` | Registro y evaluación de Actors |
| POST | `/admin/apify/actors/{id}/test-run` | Ejecuta con `maxItems≤5` y un `maxTotalChargeUsd` mínimo |
| GET | `/admin/apify/runs` | Historial de ejecuciones |
| GET | `/admin/ai/routes` | Enrutamiento por tarea (proveedor, modelo, esfuerzo, respaldo) |
| PUT | `/admin/ai/routes/{promptKey}` | Cambiar proveedor (DeepSeek o Claude), modelo o respaldo de una tarea. Rechaza proveedores desactivados o sin clave. |
| GET | `/admin/ai/models?provider=CLAUDE\|DEEPSEEK` | Modelos que informa el proveedor (Claude `GET /v1/models`), para el selector |
| GET/POST | `/admin/prompts` · `/admin/prompts/{key}/versions` · POST `/admin/prompts/{key}/versions/{v}/activate` | Gestión de prompts |
| GET | `/admin/audit-logs` | Log de auditoría |

### Operación
`GET /actuator/health` (público, sin detalles), `/actuator/info`. Los demás endpoints de actuator
están restringidos a administradores o a localhost. `GET /api/docs` sirve la interfaz OpenAPI (solo
en el perfil dev).
