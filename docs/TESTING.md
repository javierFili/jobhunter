# JobHunter AI — Estrategia de pruebas

> Sin Docker / Testcontainers. Ninguna llamada real a APIs de pago en las pruebas automatizadas
> (NFR-07).

## 1. Backend

| Nivel | Herramientas | Alcance | Se ejecuta con |
|-------|--------------|---------|----------------|
| Unitarias | JUnit 6, Mockito, AssertJ | Servicios de dominio: deduplicación, URL canónica, normalización, clasificador de elegibilidad, puntuador determinista, validador de veracidad, máquina de estados, control de presupuesto, cifrado, renderizador de prompts | `./mvnw test` (Surefire, `*Test`) — sin BD |
| Conectores | WireMock u OkHttp MockWebServer + fixtures JSON grabados en `src/test/resources/fixtures/<source>/` | Cada conector: mapeo, paginación, reintento ante 429/5xx, timeouts, payloads mal formados | `./mvnw test` |
| Proveedores de IA | MockWebServer | DeepSeek: éxito, modo JSON con contenido vacío, líneas en blanco de keep-alive, 402/429/500, salida que no cumple el esquema → reparación → fallo. Claude: los mismos casos de error y `stop_reason: "refusal"` | `./mvnw test` |
| Repositorio / integración | `@SpringBootTest` / `@DataJpaTest` con `@AutoConfigureTestDatabase(replace = NONE)` contra el **esquema local `jobhunter_test`** (Flyway migra al iniciar) | Consultas, índices usados, restricciones, migraciones Flyway válidas en MySQL real | `./mvnw verify` (Failsafe, `*IT`) |
| Controladores | MockMvc (`@AutoConfigureMockMvc`) | Validación, formato de errores, autenticación, propiedad (el usuario A no puede leer datos del usuario B — una prueba por recurso) | `./mvnw verify` |
| Seguridad | spring-security-test | Vencimiento del JWT, rotación/detección de reutilización del refresco, verificación de roles, límites de tasa, validación de cargas (archivos políglotas, bomba zip, PDF cifrado) | `./mvnw verify` |
| Arquitectura | ArchUnit | Límites entre módulos, sin llamadas de controlador → repositorio, sin `domain` → `infrastructure` | `./mvnw test` |

Aislamiento en las pruebas de integración: cada clase de prueba vacía las tablas que toca (limpieza
con `@Sql`), o usa rollback con `@Transactional` cuando no hay trabajo asíncrono.

**No** se usa H2: los scripts de Flyway son específicos de MySQL (FULLTEXT, JSON, CHECK,
collations) y el modo MySQL de H2 solo emula un subconjunto.

## 2. Frontend

| Nivel | Herramientas | Alcance |
|-------|--------------|---------|
| Componentes | Vitest + Testing Library + jsdom | Insignias, desglose de puntuación, filtros, columna del Kanban, visor de diferencias |
| Formularios | Vitest + RHF + Zod | Mensajes de validación, campos obligatorios, rangos de fechas, rango salarial |
| Integración con la API | Handlers de MSW | Flujos de consultas/mutaciones, 401 → refresco → reintento, avisos de error |
| Flujos críticos | Vitest + MSW (estilo integración) | Inicio de sesión → perfil → cargar CV → revisar importación; búsqueda → detalle de oferta → analizar; optimizar CV → comparar → aprobar; preparar postulación → copiar textos / descargar PDFs → marcar como enviada |

Opcional más adelante: E2E con Playwright contra un backend ejecutándose localmente con el esquema
de pruebas (sin Docker).

## 3. Datos de prueba

- Los fixtures viven solo en `src/test` / `frontend/src/test`, con nombres de archivo que empiezan
  por `test-` y contenido marcado (`"company_name": "TEST DATA — Example Corp"`).
- CVs de ejemplo: PDF/DOCX sintéticos generados para las pruebas (persona ficticia, claramente
  etiquetada).
- Un perfil `dev-seed` (opcional) puede cargar datos de demostración en un esquema de desarrollo
  **separado**; nunca en la base de datos real.

## 4. Verificaciones manuales / de contrato (fuera de CI)

- `scripts/smoke-<integration>.sh` realiza una sola llamada, la más barata, con credenciales reales
  (manual).
- Prueba de regresión de prompts contra el proveedor configurado en cada ruta (DeepSeek o Claude)
  con entradas fijas; los resultados se revisan a mano y el costo se registra.

## 5. Criterios de calidad por fase

- Todas las pruebas unitarias + de integración en verde.
- Nueva lógica de dominio con ≥ 80% de cobertura de líneas (reporte JaCoCo; no es un criterio
  estricto para el código de unión de la interfaz).
- `npm run lint` + `tsc --noEmit` sin errores.
- Documentación actualizada (CHANGELOG + documento correspondiente).
