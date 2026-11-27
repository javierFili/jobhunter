# JobHunter AI — Roadmap

Cada fase termina con pruebas, una revisión de arquitectura, actualización de la documentación y un
informe de lo entregado y lo pendiente. **La siguiente fase comienza solo tras la aprobación
explícita del usuario.** Las Fases 1–4 fueron aprobadas el 2026-11-26.
Las estimaciones de tamaño son relativas (S/M/L) y no son compromisos.

| Fase | Estado | Alcance | Entregables principales | Tamaño | Depende de |
|------|--------|---------|-------------------------|--------|-----------|
| **0** | ✅ Terminada | Investigación y planificación | `/docs/*` (este conjunto) | — | — |
| **1** | ✅ Implementada (pendiente de revisión) | Base | Proyecto Maven (Boot 4.2.0, wrapper) y proyecto Vite; MySQL 8.0.46 + Flyway V1–V3; autenticación (registro, inicio de sesión, JWT, rotación del token de refresco, roles); CRUD del perfil (perfiles, experiencias, proyectos, educación, certificaciones, habilidades con nivel de evidencia, idiomas, preferencias); estructura de la app (barra lateral, temas, rutas); dashboard inicial (estados vacíos más completitud del perfil); reglas ArchUnit; scripts locales sin CI | L | 0 |
| **2** | ✅ Implementada (pendiente de revisión) | Gestión de currículums | Carga + validación; extracción con PDFBox/POI; analizador heurístico de secciones (+ análisis con IA con consentimiento, opcional); interfaz de revisión de importación; documento de currículum estructurado; editor; 2 plantillas ATS (Thymeleaf → openhtmltopdf); versionado; descarga en PDF | L | 1 |
| **3** | ✅ Implementada (pendiente de revisión) | Descubrimiento de empleos | SPI de conectores; conectores Himalayas, Remotive, Jobicy, RemoteOK, Greenhouse, Lever, Ashby; SerpApi; Adzuna (opcional); ejecutor genérico de Apify + registro de Actors (desactivado por defecto); ingesta, normalización, deduplicación, clasificador de elegibilidad/alcance remoto, etiquetado de tecnologías; búsquedas guardadas, planificador basado en BD, historial de ejecuciones, notificaciones de nuevas ofertas; interfaz Descubrir con filtros y detalle de oferta; **panel de integraciones** (interruptor + credenciales de solo escritura + límites + prueba para Adzuna, Remotive, SerpApi, Apify y fuentes gratuitas; Apify sembrado **desactivado**) | XL | 1 |
| **4** | ✅ Implementada (pendiente de revisión) | Motor de IA | `AiProvider` + DeepSeek + **Claude** (SDK oficial), ambos de primer nivel; enrutamiento por tarea elegible por el usuario (DeepSeek o Claude por tarea, sembrado con DeepSeek) + respaldo + consentimiento por proveedor; pantalla "AI routing"; registro y siembra de prompts; validación y reparación de esquemas; seguimiento de uso y costo; presupuestos; análisis de ofertas; matching determinista + IA; ofertas recomendadas; interfaz de desglose de coincidencia | L | 3 |
| **5** | ✅ Implementada (pendiente de revisión) | Optimización de currículum y cartas de presentación | Instantánea del perfil; prompt de optimización de CV; validador de veracidad; interfaz de comparación; flujo de aprobación; salida multilingüe (ES/EN/IT/DE/FR); cartas de presentación en 5 formatos; exportación a PDF | L | 2, 4 |
| **6** | ✅ Implementada (pendiente de revisión) | Postulaciones (sin envío de correos) | Pipeline + máquina de estados; Kanban + tabla; paquete de postulación preparado (CV en PDF, carta de presentación, texto del correo o mensaje listo para copiar, lista de verificación); botón "Marcar como enviada" (`POST /applications/{id}/mark-submitted`) que registra un evento de postulación; recordatorios de seguimiento. El usuario envía cada postulación por su cuenta. | L | 5 |
| **7** | ✅ Implementada (pendiente de revisión) | Analítica y refinamiento | KPIs y gráficos del dashboard; información del mercado (observado vs IA); monitoreo de costos; exportación/eliminación de datos; rendimiento (planes de consulta, índices); auditoría de seguridad (revisión de dependencias, cabeceras, checklist de pruebas de penetración); guía de instalación en producción validada en una VM Ubuntu limpia | M | 6 |

## Desglose sugerido de la Fase 1

1. Estructura del repositorio: `backend/`, `frontend/`, `.env.example`, `scripts/db-setup.sql`.
2. Flyway V1 (identidad) y módulo de autenticación, con pruebas.
3. Flyway V2/V3 (perfil + siembra de habilidades) y módulo de perfil, con pruebas.
4. Frontend: páginas de autenticación, layout, tema, editor de perfil.
5. Esqueleto del dashboard, informe de la Fase 1.

## Futuro (después de v1)

- SaaS multiusuario: claves de integración a nivel usuario, cuotas por usuario, revisar de nuevo las
  licencias de las fuentes (Remotive, Adzuna, Arbeitnow), política de privacidad, revisión RGPD de
  los proveedores de IA.
- Proveedor OpenAI (u otro proveedor fuera de la RPC) para datos del CV.
- Webhooks de Apify; E2E con Playwright; i18n de la interfaz al español.
- Más fuentes ATS (Recruitee, SmartRecruiters, Workable) después de verificar sus términos.
