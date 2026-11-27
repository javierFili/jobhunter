# JobHunter AI — Control de costos

> Precios verificados el 2026-11-26 (fuentes en `INTEGRATIONS.md`). Todos los precios se guardan
> como configuración editable, nunca fijos en el código.

## 1. Fuentes de costo y costo mensual esperado (un usuario)

| Fuente | Base de precio | Uso previsto | Costo mensual est. |
|--------|----------------|--------------|-------------------|
| Himalayas, Remotive, Jobicy, RemoteOK, Greenhouse, Lever, Ashby | Gratis (limitado por términos) | Unas pocas llamadas/día por fuente; portales ATS 1×/día por empresa | $0 |
| Adzuna | Clave gratuita (uso personal); licencia para uso comercial | ≤ 200 llamadas/mes | $0 (personal) |
| SerpApi | Plan gratuito: 250 búsquedas/mes, 50/hora | ~8 búsquedas/día | $0 (≤ 250); si no, $25/mes por 1k |
| Apify | Plan gratuito: $5 de uso/mes; Actors alrededor de $1–4 por 1k resultados | Desactivado por defecto; si se activa ≈ 1–2k resultados/mes | $0–5 (dentro del crédito gratuito) |
| DeepSeek | Por token, `deepseek-flash` / `deepseek-v4-pro` | Ver `AI_DESIGN.md` §3 | ≈ $3–7 |
| Claude (Anthropic) | Por token. Opus 5.5 $4/$20, Sonnet 5.5 $2/$10, Haiku 5.5 $0.10/$0.50 por 1M; lotes −50% | Depende del enrutamiento (`AI_DESIGN.md` §3) | $0 si no se usa · ≈ $5 solo para CV + cartas en Opus 5.5 · ≈ $80+ si todas las tareas usan Opus 5.5 |
| Affinda (opcional) | $0.10/documento | Desactivado por defecto | $0 |
| **Total** | | | **≈ $3–12/mes** solo con DeepSeek (Apify desactivado). Sumar el monto de Claude si se le asignan tareas. |

No hay costos de envío de correo: el envío de correos se descartó del alcance (decisión del usuario,
2026-11-26).

Cada proveedor de IA tiene su **propio presupuesto mensual** (`integration_settings.monthly_budget_usd`).
Cuando se agota el presupuesto, la ruta usa el respaldo solo si hay uno configurado.

## 2. Controles

| Control | Mecanismo |
|---------|-----------|
| **Presupuestos** | `integration_settings.monthly_budget_usd` (IA, Apify, SerpApi) más topes diarios/mensuales de solicitudes. `BudgetGuard.check(integration, estimatedCost)` se ejecuta **antes** de cada llamada de pago y la rechaza con `BUDGET_EXCEEDED`. Notificaciones de aviso al 80% y al 100%. |
| **Tope por ejecución de Apify** | Cada ejecución envía `maxTotalChargeUsd` (por defecto $0.50) y `maxItems`. Al alcanzar el tope, la plataforma aborta la ejecución y no cobra por encima. Nuestra propia verificación previa también lee `/v2/users/me/limits` (`current.monthlyUsageUsd`). Se recomienda fijar además el tope mensual a nivel de cuenta en la consola de Apify. |
| **Timeouts de ejecución de Apify** | `timeout` por Actor (por defecto 300 s). El sondeador aborta las ejecuciones que superan el plazo. |
| **Cuota de SerpApi** | Verificación de la cuenta gratuita (`plan_searches_left`, `this_hour_searches`) antes de los lotes programados. Nunca se envía `no_cache`, así que las búsquedas idénticas dentro de 1 h son gratis. La paginación se limita a N páginas (por defecto 2). |
| **Cortesía con fuentes gratuitas** | Intervalos mínimos fijos: Remotive ≥ 6 h, RemoteOK ≥ 6 h, portales ATS ≥ 24 h por empresa. Una sola descarga se comparte entre todas las búsquedas guardadas (filtrado local). |
| **Deduplicación antes de la IA** | El análisis con IA se ejecuta solo sobre `job_offers` nuevas (no sobre referencias duplicadas), y solo si pasan el prefiltro determinista (no `NOT_ELIGIBLE`, coincidencia de habilidades ≥ umbral, no ocultas). |
| **Caché de IA** | `ai_analyses.cache_key` es único; un acierto cuesta $0. Solo se vuelve a analizar ante un cambio material del contenido (`AI_DESIGN.md` §7). |
| **Enrutamiento de modelos** | El usuario elige el proveedor (DeepSeek o Claude) y el modelo para cada tarea en "AI routing". Las rutas iniciales usan DeepSeek: `deepseek-flash` barato y sin razonamiento para extracción y puntuación, `deepseek-v4-pro` para la optimización del CV. Elegir Claude para una tarea aumenta el costo según `AI_DESIGN.md` §3. |
| **Caché de prefijo del prompt** | Las instrucciones estáticas van primero, para que la caché automática de DeepSeek (y `cache_control` en Claude) tenga aciertos. En DeepSeek, la entrada con acierto de caché cuesta alrededor del 2% de un fallo. |
| **Lotes fuera de hora pico** | Los lotes de IA no urgentes (re-puntuación, recomendaciones de habilidades) se programan fuera de las ventanas pico de DeepSeek (01–04 y 06–10 UTC en días hábiles) para obtener precios 50% más bajos. |
| **Truncamiento de la entrada** | Descripción de la oferta limitada (por defecto 12k caracteres) tras quitar el HTML y el texto repetitivo (secciones de beneficios/EEO detectadas heurísticamente). |
| **Procesamiento por lotes** | La cola de IA procesa hasta N ofertas por ciclo con una concurrencia de 2. Las búsquedas están limitadas por `max_concurrent_searches` (por defecto 2). |
| **Reintentos** | Como máximo 2 reintentos, solo ante errores reintentables. El JSON inválido tiene 1 intento de reparación. Cada intento se registra y se costea. |
| **Circuit breakers** | Por proveedor. Circuito abierto → las llamadas se omiten y la ejecución de la fuente se marca `SKIPPED`, sin que se acumulen reintentos. |
| **Límites de programación** | Intervalo mínimo de 60 min por búsqueda guardada, máximo 10 búsquedas programadas activas por usuario (configurable), un solo hilo despachador. |

## 3. Contabilidad de uso

- Cada llamada externa escribe en `api_usage_logs` (integración, operación, solicitudes, tokens,
  resultados, costo y si el costo es una estimación).
- El costo de Apify es definitivo solo después de volver a leer la ejecución (`usageTotalUsd`)
  unos segundos después de que termina. Hasta entonces se guarda como estimación y luego se corrige.
- El costo de DeepSeek se calcula a partir del `usage` devuelto (tokens con acierto/fallo de caché
  y de salida) y del precio pico/fuera de pico al momento de la solicitud.
- El saldo de DeepSeek (`GET /user/balance`) y los datos de la cuenta de SerpApi se muestran en la
  página de Integraciones y se actualizan como máximo cada hora.
- Dashboard: costo por día por integración, costo por oferta relevante descubierta, costo por
  postulación.
