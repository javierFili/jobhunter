# JobHunter AI

Asistente personal de búsqueda de empleo: descubre ofertas reales en fuentes legítimas, evalúa si son accesibles
desde Bolivia y su compatibilidad con tu perfil verificado, gestiona tus CV y (próximas fases) los optimiza y prepara
postulaciones que **tú** envías.

- **Backend:** Java 21 · Spring Boot 4.2 · MySQL 8 · Flyway — `backend/`
- **Frontend:** React 19 · TypeScript · Vite · Tailwind 4 · shadcn/ui — `frontend/`
- **IA:** DeepSeek y Claude, elegibles por tarea desde el panel de Integraciones.
- **Sin Docker.** Todo corre directamente en Ubuntu.
- Documentación completa (en español): [`docs/`](docs/) — empieza por `docs/PHASE0_REPORT.md` y `docs/ROADMAP.md`.

## Puesta en marcha (desarrollo)

```bash
# 1. Base de datos (una sola vez)
mysql -u root -p < scripts/db-setup.sql   # o: sudo mysql < scripts/db-setup.sql

# 2. Variables de entorno
cp .env.example .env
#   completa JOBHUNTER_JWT_SECRET (openssl rand -base64 48) y JOBHUNTER_MASTER_KEY (openssl rand -base64 32)

# 3. Arrancar todo
scripts/dev.sh            # API: http://localhost:8095 · UI: http://localhost:5175
```

El **primer usuario registrado** es el administrador (accede a Integraciones).

## Producción (Ubuntu, sin Docker)

`deploy/jobhunter.service` (systemd), `deploy/nginx-jobhunter.conf` (TLS + SPA + proxy a la API) y
`scripts/backup.sh` (respaldos cifrados). Pasos detallados en `docs/DEVELOPMENT_SETUP.md` §7.

## Pruebas

```bash
cd backend && ./mvnw test      # unitarias + arranque completo de la app (no necesitan BD)
cd backend && ./mvnw verify    # + integración contra el esquema local jobhunter_test (MySQL real, sin Docker)
cd frontend && npm test        # vitest + MSW
```

Ninguna prueba automática llama a APIs reales de pago: se usan servidores locales simulados.
