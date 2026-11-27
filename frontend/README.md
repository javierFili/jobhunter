# JobHunter AI — Frontend

SPA en React 19 + Vite 8 + TypeScript 6, Tailwind CSS 4 y shadcn/ui. Interfaz en español.

## Requisitos

- Node.js 22.12 o superior (probado con 22.23) y npm 10.

## Puesta en marcha

```bash
npm install
npm run dev        # http://localhost:5175
```

El servidor de desarrollo redirige `/api` → `http://localhost:8095` (backend Spring Boot).
Sin backend la app carga, pero redirige a `/login` y las peticiones fallan.

## Scripts

| Comando              | Descripción                                   |
| -------------------- | --------------------------------------------- |
| `npm run dev`        | Servidor de desarrollo (puerto 5175)          |
| `npm run build`      | Chequeo de tipos + build de producción (`dist/`) |
| `npm run preview`    | Sirve el build en el puerto 5175              |
| `npm test`           | Tests (Vitest + Testing Library + MSW), sin backend |
| `npm run test:watch` | Tests en modo watch                           |
| `npm run lint`       | ESLint                                        |
| `npm run typecheck`  | `tsc` sin emitir archivos                     |

## Estructura

```
src/
  app/          router, providers, layout (sidebar + barra superior)
  components/   ui/ (shadcn) y componentes compartidos (DataTable, EmptyState, PageHeader, StatusBadge, ConfirmDialog…)
  lib/          cliente API, problem details (RFC 9457), query keys, formateadores, helpers de Zod, etiquetas en español
  features/     auth, dashboard, profile, settings, placeholders
  test/         servidor MSW, handlers y datos de prueba (TEST DATA)
```

## Secciones

- **Panel**, **Mi perfil** y **Configuración** (Fase 1).
- **Mis CV** (Fase 2): importar un CV (PDF/DOCX) con revisión antes de agregar al perfil; CV generados desde el
  perfil verificado con editor, historial de versiones, comparación y vista previa/descarga en PDF.
- **Descubrir empleos** y **Búsquedas guardadas** (Fase 3): filtros, accesibilidad desde Bolivia, atribución de
  fuentes, ejecuciones con seguimiento en vivo y empresas vigiladas (Greenhouse/Lever/Ashby).
- **Empleos recomendados** (Fase 4): compatibilidad con tu perfil, análisis de ofertas con IA y evaluación de las mejores.
- **Cartas de presentación** y **optimización de CV** (Fase 5): nunca se inventan hechos; lo no verificable queda como sugerencia.
- **Postulaciones** (Fase 6): Kanban (arrastrar y soltar, también con teclado) y tabla; JobHunter nunca envía nada por ti.
- **Analítica** (Fase 7): indicadores, gráficos, demanda observada de tecnologías y recomendaciones de IA (separadas).
- **Configuración**: exportar tus datos (ZIP) y eliminar tu cuenta.
- **Integraciones** (solo administradores): credenciales (solo escritura), límites, fuentes, Actors de Apify y tipos de cambio.

## Autenticación

El access token se guarda solo en memoria. El refresh token es una cookie HttpOnly (`jh_refresh`)
gestionada por el backend; ante un 401 el cliente llama una vez a `POST /api/v1/auth/refresh` y reintenta.
Al iniciar la app se intenta restaurar la sesión con ese mismo endpoint.

## Componentes shadcn

Para agregar componentes: `npx shadcn@4.23.1 add <componente>`.
