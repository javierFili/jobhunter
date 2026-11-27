# JobHunter AI — Informe de la Fase 0

> Fecha: 2026-11-26 · Estado: **aprobado**. Las Fases 1–4 fueron aprobadas el 2026-11-26.
> Este informe relaciona los 12 entregables de la Fase 0 con los documentos y resume las
> conclusiones. También registra las decisiones tomadas por el usuario.

## Índice de entregables

| # | Entregable | Dónde |
|---|------------|-------|
| 1 | Informe de viabilidad técnica | §1 más abajo |
| 2 | Investigación de integraciones con APIs oficiales | `INTEGRATIONS.md` §1–7 |
| 3 | Lista recomendada de integraciones | `INTEGRATIONS.md` §0 |
| 4 | Costos y limitaciones estimados de las integraciones | `COST_CONTROL.md` §1, `INTEGRATIONS.md` |
| 5 | Propuesta de arquitectura | `ARCHITECTURE.md` |
| 6 | Propuesta de esquema de base de datos | `DATABASE.md` |
| 7 | Estructura de módulos del backend | `ARCHITECTURE.md` §2 |
| 8 | Estructura de módulos del frontend | `ARCHITECTURE.md` §4 |
| 9 | Propuesta de endpoints de la API | `API.md` |
| 10 | Roadmap de desarrollo | `ROADMAP.md` |
| 11 | Consideraciones de seguridad | `SECURITY.md`, `AI_DESIGN.md` §1 y §6 |
| 12 | Requisitos de instalación en Ubuntu | `DEVELOPMENT_SETUP.md` |

## 1. Viabilidad

**En general: viable** con el stack exigido, sin Docker, en esta máquina. Tres supuestos de la
especificación deben ajustarse a la realidad:

| Supuesto de la especificación | Realidad (verificada) | Ajuste |
|-------------------------------|-----------------------|--------|
| Adzuna, Jooble y Remotive son fuentes clave | Adzuna **no cubre Bolivia ni tiene filtro de remoto**, devuelve solo fragmentos de la descripción y requiere licencia para uso comercial. Jooble necesita una clave por país, emitida tras un formulario, con 100 solicitudes/día; la cobertura de Bolivia no está clara. Remotive es gratuita, pero pequeña (16 ofertas activas), con 24 h de retraso y limitada a unas 4 llamadas al día. | La oferta principal pasa a ser **Himalayas** (restricciones de país y zona horaria estructuradas), Remotive, Jobicy, RemoteOK, **portales ATS de empresas objetivo** y **SerpApi** para una cobertura amplia. Adzuna es opcional; Jooble espera hasta que se conceda el acceso. |
| Se puede buscar en Greenhouse, Lever y Ashby | Son **portales por empresa sin búsqueda global**, y sin filtros por palabra clave ni fecha. | Una función de **lista de empresas a vigilar** (board tokens) con filtrado local. Ventaja: estos portales están completos, así que se puede detectar con exactitud cuándo se retira una oferta. |
| Apify da acceso a LinkedIn, Indeed y Glassdoor | Solo existen Actors **creados por la comunidad**. LinkedIn (Acuerdo de Usuario §8.2) e Indeed (Términos §21) prohíben explícitamente el scraping. | Un ejecutor genérico de Actors configurado por el administrador, con `maxTotalChargeUsd` por ejecución. **Los Actors están desactivados por defecto**, y activar uno es una decisión explícita y documentada del usuario. |

Otros hechos verificados que cambian el diseño:
- **Los modelos de DeepSeek ahora son `deepseek-flash` y `deepseek-v4-pro`.** `deepseek-chat` y
  `deepseek-reasoner` no aparecen en la documentación actual.
- El modo JSON puede devolver **contenido vacío**, lo cual debe manejarse.
- **DeepSeek almacena los datos personales en la RPC.** Por eso el diseño agrega una pantalla de
  consentimiento y la eliminación de datos personales (PII).
- **El envío de correos se descartó del alcance** por decisión del usuario: la aplicación prepara
  la postulación y el usuario la envía por su cuenta.
- **MySQL 8.0 está fuera de soporte (abril de 2026).** Por decisión del usuario se mantiene 8.0.46;
  el código sigue siendo compatible con 8.4 LTS para una actualización futura.
- **El soporte OSS de Spring Boot 3.5 terminó.** Se usa Boot 4.x, que trae Jackson 3
  (`tools.jackson`) y starters modulares.
- **TypeScript 7.0 es demasiado nuevo para typescript-eslint**, así que TypeScript se fija en 6.0.3.

Costo de operación esperado para un usuario: **alrededor de US$3–12/mes** (`COST_CONTROL.md`).

## 2. Riesgos principales

| Riesgo | Impacto | Probabilidad | Mitigación |
|--------|---------|--------------|-----------|
| Pocas ofertas realmente elegibles desde Bolivia en las fuentes gratuitas | Poco valor | Media | Combinar portales remote-first + lista de vigilancia ATS + SerpApi con valores `gl` de LATAM; medir el rendimiento en la Fase 3 antes de agregar fuentes de pago |
| Clasificación errónea de la elegibilidad ("remoto" que en realidad es solo EE. UU.) | Postulaciones desperdiciadas | Alta | Clasificador basado en evidencia; `UNKNOWN`/`NEEDS_REVIEW` por defecto; evidencia citada en la interfaz; muestreo manual |
| Cambian los términos de una fuente o se revoca el acceso | Pérdida de funciones | Media | Aislamiento de conectores; notas de términos por fuente; límites de cortesía más estrictos de lo exigido |
| La IA inventa contenido del CV | Credibilidad y ética | Media | Referencias a IDs de hechos + validador de veracidad en Java + marcas `SUGGESTED` + revisión obligatoria |
| Inyección de prompts en las ofertas | Puntuaciones o textos manipulados | Media | Envoltura de contenido no confiable, validación de esquema, herramientas de IA sin efectos secundarios, aprobación humana |
| Privacidad / jurisdicción de DeepSeek | Exposición legal (SaaS, usuarios de la UE) | Media | Consentimiento, minimización, abstracción de proveedores, revisión legal antes del SaaS |
| Sobrecosto (Apify, IA) | Dinero | Baja | Topes por ejecución y mensuales, verificados antes de cada llamada |
| Boot 4.2.0 es muy nuevo | Errores tempranos | Baja–media | Plan de contingencia: volver a 4.1.3 (Boot 4.1 ya se usó en esta máquina) |
| Poco espacio en disco (19 GB libres) | Fallos de compilación | Media | Liberar espacio antes de la Fase 1 |

## 3. Decisiones

### Decididas por el usuario (2026-11-26)
- **API de Claude** agregada como segundo proveedor de IA, junto a DeepSeek (`AI_DESIGN.md`
  §2.0–2.1b).
- **Apify se entrega desactivado.** Por ahora el descubrimiento usa solo las fuentes directas. Se
  puede activar más adelante desde el panel.
- **Adzuna, Remotive, SerpApi** (además de DeepSeek, Claude y Apify) tienen cada una un interruptor
  y gestión de credenciales en el panel de Integraciones (`INTEGRATIONS.md` §0). Remotive no tiene
  campo de clave porque su API pública no la usa.
1. **Fuentes de la Fase 3 aprobadas:** Himalayas, Remotive, Jobicy, RemoteOK, Greenhouse, Lever,
   Ashby, SerpApi (plan gratuito) y Adzuna. Apify sigue desactivado por defecto.
2. **Actors de LinkedIn/Indeed:** permanecen desactivados, ya que Apify sigue desactivado (solo
   fuentes directas por ahora).
3. **Enrutamiento de IA y consentimiento:** para cada tarea de IA (análisis de ofertas, matching,
   análisis de CV, generación/optimización de CV, cartas de presentación) el usuario elige DeepSeek
   o Claude desde la pantalla "AI routing". Ambos proveedores son de primer nivel; las rutas se
   siembran con DeepSeek. El consentimiento sigue siendo por proveedor.
4. **Correo:** el envío de correos se **descarta del alcance**. La aplicación prepara el paquete de
   postulación (CV en PDF, carta de presentación, texto del correo o mensaje listo para copiar,
   lista de verificación); el usuario lo envía por su cuenta y pulsa "Marcar como enviada"
   (`POST /applications/{id}/mark-submitted`). Se eliminan Resend, SMTP y `email_logs`.
5. **MySQL:** se mantiene 8.0.46. La 8.0 está fuera de soporte; el código sigue siendo compatible
   con 8.4 para una actualización futura.
6. **Spring Boot:** 4.2.0 (se resuelve desde Maven Central y tiene el soporte OSS más largo, hasta
   2027-12-31). 4.1.3 queda como plan de contingencia, no como pregunta abierta.
7. **Repositorio:** se creó un repositorio git separado dentro de `jobhunter/`.
8. **Idioma de la documentación:** español. Los identificadores de código siguen en inglés.
9. **Puertos:** API 8095, servidor de desarrollo de Vite 5175.
10. **Fases 1–4 aprobadas** el 2026-11-26.

### Pendientes
Ninguna.

## 4. Pendientes de verificación manual

Ver `INTEGRATIONS.md` §9. Los puntos principales:
- Cuota y precio de licencia de Adzuna.
- Aprobación del acceso a Jooble.
- Términos de uso de Greenhouse, Lever y Ashby.
- El parámetro de DeepSeek que desactiva el modo de razonamiento (thinking).
- Retención de datos de la API de DeepSeek.
