# JobHunter AI — Visión

> Estado: Fase 0 cerrada; Fases 1–4 aprobadas el 2026-11-26. Última actualización: 2026-11-26.

## 1. Problema

Un desarrollador de software radicado en Bolivia que busca trabajo remoto internacional, proyectos
freelance o puestos en Europa / Latinoamérica enfrenta cuatro problemas recurrentes:

1. **Oferta fragmentada.** Las oportunidades están repartidas en decenas de portales de empleo,
   páginas de empleo de ATS (Greenhouse, Lever, Ashby…), agregadores y buscadores. Cada uno tiene
   filtros distintos, y la mayoría no indica si un empleo "remoto" está realmente abierto a alguien
   que vive en Bolivia.
2. **Ruido de elegibilidad.** "Remoto" con frecuencia significa "remoto dentro de EE. UU." o
   "remoto dentro de la UE". Postular a esas ofertas hace perder tiempo y daña la credibilidad.
3. **Costo de adaptación.** Una postulación competitiva necesita un CV y una carta de presentación
   adaptados al puesto, en el idioma correcto (ES / EN / IT / DE / FR). Hacerlo a mano limita el
   volumen.
4. **Carga de seguimiento.** Llevar el control de qué se envió, con qué versión del CV y cuándo
   hacer seguimiento es manual y propenso a errores.

## 2. Producto

JobHunter AI es un asistente de carrera inteligente que:

- **Descubre** oportunidades en fuentes legítimas y documentadas (APIs oficiales, portales públicos
  de ATS, APIs de búsqueda con licencia y Actors de Apify cuidadosamente evaluados; Apify está
  desactivado por defecto).
- **Normaliza y deduplica** las ofertas en un único catálogo, conservando cada fuente y URL
  original.
- **Evalúa la elegibilidad y el ajuste** con una puntuación transparente que combina reglas
  deterministas (ubicación, idioma, salario, tipo de contrato) y análisis semántico con IA
  (habilidades, experiencia), explicando siempre el *porqué*.
- **Optimiza CVs y redacta cartas de presentación** por oportunidad, **sin inventar nada**: cada
  afirmación se puede rastrear hasta el perfil verificado, o se marca explícitamente como
  sugerencia que requiere confirmación del usuario.
- **Prepara las postulaciones** (CV en PDF, carta de presentación, texto del correo o mensaje listo
  para copiar y lista de verificación) y les da seguimiento en un pipeline (Kanban + tabla). **La
  aplicación no envía correos:** el usuario envía la postulación por su cuenta y luego la marca
  como enviada.
- **Mide** el mercado (qué tecnologías se piden y dónde) y su propio costo (tokens de IA, gasto en
  Apify, cuotas de API).

## 3. Principios (no negociables)

| # | Principio | Consecuencia en el diseño |
|---|-----------|---------------------------|
| P1 | **Solo datos reales** | No hay empleos falsos en el producto. Los datos de prueba viven en `src/test` y están etiquetados como tales. |
| P2 | **CVs fieles a los hechos** | El optimizador puede reordenar, reformular y enfatizar; no puede crear empresas, fechas, años, certificaciones, proyectos, tecnologías ni niveles de idioma. Lo garantiza un validador posterior a la generación, no solo el prompt. |
| P3 | **Humano en el circuito** | La aplicación nunca envía postulaciones ni correos. El usuario las envía por su cuenta y registra el envío de forma explícita ("Marcar como enviada"). |
| P4 | **Elegibilidad basada en evidencia** | Una oferta solo es "accesible desde Bolivia" si el texto o los metadatos lo respaldan; de lo contrario, `NEEDS_REVIEW`. |
| P5 | **Respeto a las plataformas** | Sin evasión de CAPTCHA, sin automatizar inicios de sesión, sin scraping que viole los términos. Cada fuente tiene una evaluación legal y de términos documentada antes de activarse. |
| P6 | **Económico por diseño** | Primero procesamiento determinista, deduplicación antes de la IA, análisis en caché y límites de gasto estrictos. |
| P7 | **Privacidad** | Los datos del CV se envían a un proveedor de IA externo solo con consentimiento explícito por proveedor. La exportación y eliminación de datos personales son funciones de primer nivel. |
| P8 | **Entrada no confiable** | Las descripciones de empleo y los documentos cargados son datos, nunca instrucciones (defensa contra inyección de prompts). |

## 4. Usuarios

- **Inicial (v1):** un solo usuario — un desarrollador de software de Bolivia (Java / Spring,
  Laravel, full-stack) que busca puestos remotos a nivel mundial, en LATAM o en Europa, trabajos
  freelance y puestos internacionales.
- **Futuro:** SaaS multiusuario. Cada tabla con datos del usuario lleva `user_id` desde el primer
  día; los roles (`USER`, `ADMIN`) existen desde la Fase 1; las credenciales de integración se
  dividen en *nivel sistema* (admin) y, más adelante, *nivel usuario*.

## 5. Fuera de alcance (por ahora)

- **Envío de correos** desde la aplicación (descartado por decisión del usuario el 2026-11-26). La
  aplicación prepara el paquete de postulación y el usuario lo envía manualmente.
- Envío automático a formularios de terceros (solo asistencia guiada; posible más adelante con
  permiso explícito por sitio).
- Scraping directo de LinkedIn/Indeed/Glassdoor. Solo mediante Actors de Apify, tras una evaluación
  documentada de términos y riesgos y una activación explícita del administrador (ver
  `INTEGRATIONS.md`).
- Apps móviles nativas, pagos/facturación, organizaciones multi-tenant.

## 6. Métricas de éxito

- Nuevas ofertas relevantes (coincidencia HIGH/MEDIUM + elegibles) por semana.
- Proporción de ofertas descubiertas correctamente clasificadas según la elegibilidad para Bolivia
  (muestreo manual).
- Tiempo desde "oferta descubierta" hasta "postulación lista" (meta: < 10 minutos con revisión).
- Postulaciones enviadas por semana, tasa de respuesta, tasa de entrevistas.
- Costo operativo mensual (IA + Apify + APIs) dentro del presupuesto configurado.
