# JobHunter AI — Configuración del entorno de desarrollo (Ubuntu, sin Docker)

> Propuesta de la Fase 0 (aprobada). La máquina se inspeccionó el 2026-11-26, y las versiones se
> verificaron contra Maven Central, el registro de npm y la API de soporte de spring.io en la misma
> fecha.
> **No se usan Docker, Docker Compose, Testcontainers ni Kubernetes en ninguna parte.**

## 1. Máquina actual (inspeccionada)

| Elemento | Encontrado | Estado |
|----------|------------|--------|
| SO | Ubuntu 22.04.5 LTS, x86_64, 8 núcleos, 15 GiB de RAM | OK |
| Disco | **19 GB libres (92% usado)** | ⚠ Justo. Las cachés de Maven y npm más MySQL usarán 2–4 GB. Liberar algo de espacio. |
| Java | OpenJDK 21.0.13 (también está instalado el 11) | OK, cumple el requisito de Java 21 LTS |
| Maven | 3.6.3 del sistema | ⚠ Es exactamente el mínimo de Spring Boot 4. **Usar el Maven Wrapper (3.9.16)**, incluido en el repositorio. |
| Node / npm | 22.23.5 / 10.9 | OK. El requisito más estricto es react-router `>=22.22.0`, que se cumple. |
| MySQL | 8.0.46 (paquete de Ubuntu), en ejecución en el 3306 | ⚠ **MySQL 8.0 llegó al fin de su soporte en abril de 2026.** Se mantiene por decisión del usuario; ver §4. El usuario root necesita contraseña, así que crear el esquema es un paso manual. |
| Puertos en uso | 3306, 5173, 5432/5433, 6379/6380, 7070, 8000, 8025, 8080, 80 | JobHunter usa **8095** (API) y **5175** (servidor de desarrollo de Vite) |
| Herramientas | git 2.34, curl, pdftotext, LibreOffice | LibreOffice no es necesario |
| Docker | Instalado | **No se usa** |

## 2. Decisiones de versiones

### 2.1 Backend

| Componente | Versión | Fuente / nota |
|------------|---------|---------------|
| Java | 21 LTS | — |
| Spring Boot | **4.2.0** (elegida por el usuario) | Central lista 4.2.0 (2026-11-19). La API de generaciones de spring.io indica para 4.2.x lanzamiento inicial el 2026-11-30 y soporte OSS hasta 2027-12-31, el más largo disponible. Plan de contingencia: **4.1.3** (soporte OSS de 4.1.x hasta 2027-07-31), solo si 4.2.0 presenta problemas. **El soporte OSS de 3.5.x terminó el 2026-06-30, así que no se usa.** |
| Spring Framework / Security | 7.1.0 / 7.2.0 | Gestionadas por Boot 4.2.0 |
| Hibernate ORM / Validator | 7.4.15 / 9.1.6 | Gestionadas |
| Jackson | **3.1.8** (paquetes `tools.jackson`) | Predeterminado en Boot 4. Las anotaciones siguen en `com.fasterxml.jackson.annotation`. |
| Driver de MySQL | `com.mysql:mysql-connector-j` (gestionado, 26.10.0) | — |
| Flyway | Gestionado 12.11.0 + **`org.flywaydb:flyway-mysql`** + `spring-boot-starter-flyway` | El soporte de MySQL es un módulo open source aparte. 8.0 y 8.4 están soportadas en la edición community (verificado en el código fuente). |
| springdoc-openapi | `springdoc-openapi-starter-webmvc-ui` **3.2.1** | La línea 3.x es para Boot 4 |
| JWT | `spring-boot-starter-security-oauth2-resource-server` (Nimbus, HS256) | El artifact id exacto del starter se confirma en la Fase 1. No se usa jjwt, porque su integración con Jackson apunta a Jackson 2. |
| Reintentos / concurrencia | **Integrados en Spring Framework 7**: `@Retryable`, `@ConcurrencyLimit`, `RetryTemplate` | Verificado en la documentación de referencia de SF 7. Boot ya no gestiona Spring Retry. |
| Circuit breaker | `io.github.resilience4j:resilience4j-circuitbreaker` **2.4.0**, usado de forma programática sin el starter de Boot | El starter `resilience4j-spring-boot4` está construido contra Boot 4.0. Su funcionamiento en 4.2 no está verificado, así que se evita el starter. |
| Limitación de tasa | `com.bucket4j:bucket4j_jdk17-core` **8.23.0** | No el artifact desactualizado `bucket4j-core` |
| Bloqueo del planificador | `shedlock-spring` + `shedlock-provider-jdbc-template` **7.12.0** | — |
| Texto de PDF | Apache PDFBox **3.0.8** | — |
| Texto de DOCX | Apache POI **5.5.1** (`poi-ooxml`) | Existe POI 6.0.0, pero no se verificó su versión mínima de Java; se actualizará más adelante. No se usa Tika 4.2 porque es demasiado pesado para dos formatos. |
| HTML → PDF | `io.github.openhtmltopdf:openhtmltopdf-pdfbox` **1.1.117** (LGPL-2.1+) + Thymeleaf 3.1.7 | Un fork activo de openhtmltopdf. La LGPL no es problema como dependencia sin modificar. |
| Cliente de Claude | `com.anthropic:anthropic-java` **3.14.0** | SDK oficial (Maven Central, 2026-11-24). Trae Jackson 2.19 y OkHttp. La convivencia con Jackson 3 de Boot 4 se verifica en la Fase 4. |
| Saneamiento de HTML | OWASP Java HTML Sanitizer | Versión por fijar en la Fase 3 |
| Mapeo | MapStruct | Versión por fijar en la Fase 1 |
| Pruebas | JUnit Jupiter 6.1.3, Mockito, AssertJ, starters de prueba de Spring Boot, ArchUnit, WireMock u OkHttp MockWebServer | Versiones gestionadas cuando es posible |

No se incluye Spring Mail: el envío de correos se descartó del alcance (decisión del usuario,
2026-11-26).

Notas de migración a Boot 4 que afectan al código desde el primer día:
- Usar `spring-boot-starter-webmvc` (`starter-web` está obsoleto).
- Usar `@MockitoBean` (`@MockBean` fue eliminado).
- Las pruebas con MockMvc necesitan `@AutoConfigureMockMvc`.
- Definir un bean `JsonMapper` en lugar de un bean `ObjectMapper`.

### 2.2 Frontend

| Paquete | Versión | Nota |
|---------|---------|------|
| react / react-dom | 19.3.1 | |
| vite | 8.4.0 | Node `^20.19 \|\| >=22.12` |
| typescript | **6.0.3** (no 7.0.2) | `typescript-eslint` 8.74 requiere TS `<6.1.0` (verificado). Pasar a TS 7 cuando el linting lo soporte. |
| tailwindcss + @tailwindcss/vite | 4.3.5 | |
| shadcn (CLI) | 4.23.1 | Documentación oficial: componentes actualizados para Tailwind v4 y React 19; usa `tw-animate-css` |
| react-router | 8.5.3 | Necesita Node >=22.22 y React >=19.2.7 |
| @tanstack/react-query | 5.108.4 | |
| react-hook-form / @hookform/resolvers / zod | 7.92.0 / 7.0.9 / 4.6.7 | |
| recharts | 3.11.1 | |
| lucide-react | 1.81.0 | |
| Arrastrar y soltar | @dnd-kit/core 6.3.1 (estable) o @dnd-kit/react 0.5.3 (API nueva, anterior a 1.0) | Se decide en la Fase 6 |
| Pruebas | vitest 5.1.2, @testing-library/react 16.3.3, msw 3.0.8, jsdom | |

Las versiones exactas se fijan en `package.json` (sin `^`) para que el build sea reproducible.

## 3. Estructura del repositorio

```
jobhunter/
  backend/            # Spring Boot (Maven wrapper: ./mvnw)
    src/main/java/com/jobhunter/...
    src/main/resources/{application.yml, db/migration, prompts, templates/resume}
    src/test/java/...  # *Test = unit, *IT = integration (Failsafe, real local MySQL)
  frontend/           # React + Vite
  docs/
  scripts/            # db-setup.sql, dev.sh, backup.sh
  .env.example        # every env var, no real values
```

> `jobhunter/` tiene su propio repositorio git, separado del repositorio padre
> `~/Desktop/javier` (decisión del usuario, 2026-11-26), así que los commits y el historial quedan
> aislados.

## 4. MySQL

### 4.1 Versión

**Decisión del usuario (2026-11-26): se mantiene MySQL 8.0.46.**

MySQL 8.0 está fuera de soporte (Oracle Sustaining Support desde abril de 2026). El destino para una
actualización futura es **MySQL 8.4 LTS** (8.4.13, disponible para jammy desde repo.mysql.com
mediante `mysql-apt-config`). El soporte Premier llega hasta 2029-04, según endoflife.date; no se
pudo descargar el PDF del propio Oracle.

El código funciona en **8.0.46 y en 8.4**: no usa funciones exclusivas de 8.4, y sus cuentas usan
`caching_sha2_password`. Actualizar el servidor afecta a los demás proyectos locales que usan esta
instancia de MySQL, por eso la actualización queda para más adelante.

Cambios relevantes de 8.4 (para cuando se actualice):
- `mysql_native_password` está desactivado por defecto.
- `default_authentication_plugin` se reemplaza por `authentication_policy`.
- Cambiaron varios valores predeterminados de InnoDB.
- Se eliminó `mysql_upgrade`; el servidor se actualiza solo.

### 4.2 Esquema y usuarios (ejecutar una vez como administrador de MySQL)

```sql
CREATE DATABASE jobhunter      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE jobhunter_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'jobhunter'@'localhost'      IDENTIFIED BY '<strong-password>';
CREATE USER 'jobhunter_test'@'localhost' IDENTIFIED BY '<test-password>';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
  ON jobhunter.* TO 'jobhunter'@'localhost';
GRANT ALL PRIVILEGES ON jobhunter_test.* TO 'jobhunter_test'@'localhost';
FLUSH PRIVILEGES;
```

Se ejecuta con `mysql -u root -p < scripts/db-setup.sql` (pide la contraseña de root de MySQL). Si tu
root usa `auth_socket`, usa `sudo mysql < scripts/db-setup.sql`. Contraseñas de desarrollo:
`JhDev#2026pw` (jobhunter) y `JhTest#2026pw` (jobhunter_test); cámbialas en producción y en `.env`.

En producción, las migraciones de Flyway se ejecutan con el usuario que tiene permisos de DDL.
Opcionalmente, el usuario de ejecución de la aplicación se reduce a solo DML, y las migraciones se
ejecutan con un usuario aparte (`spring.flyway.user`/`password`).

## 5. Variables de entorno (`.env.example`)

```bash
# Database
JOBHUNTER_DB_URL=jdbc:mysql://localhost:3306/jobhunter?serverTimezone=UTC&useUnicode=true&characterEncoding=utf8
JOBHUNTER_DB_USER=jobhunter
JOBHUNTER_DB_PASSWORD=
# Security
JOBHUNTER_JWT_SECRET=          # >= 32 random bytes, base64 (openssl rand -base64 48)
JOBHUNTER_MASTER_KEY=          # AES-256 key for encrypting DB-stored secrets, base64 (openssl rand -base64 32)
JOBHUNTER_CORS_ORIGINS=http://localhost:5175
# Storage
JOBHUNTER_FILES_DIR=/home/<user>/.local/share/jobhunter/files
# Integrations (optional; can also be set in the admin UI, which stores them encrypted)
JOBHUNTER_DEEPSEEK_API_KEY=
JOBHUNTER_ANTHROPIC_API_KEY=   # Claude
JOBHUNTER_APIFY_TOKEN=         # Apify stays DISABLED in the panel even if this is set
JOBHUNTER_HIMALAYAS_API_KEY=   # optional (free key raises limits)
JOBHUNTER_SERPAPI_KEY=
JOBHUNTER_ADZUNA_APP_ID=
JOBHUNTER_ADZUNA_APP_KEY=
# Budgets
JOBHUNTER_AI_MONTHLY_BUDGET_USD=10
JOBHUNTER_APIFY_MONTHLY_BUDGET_USD=5
```

Las variables de entorno tienen prioridad sobre los valores guardados en la base de datos. `.env`
está en git-ignore. Cada clave también se puede ingresar en **Integraciones**, y **ninguna variable
es obligatoria para iniciar la aplicación**. Una variable de entorno aporta **solo una credencial**.
El interruptor de activación siempre está en el panel, así que definir `JOBHUNTER_APIFY_TOKEN` no
activa Apify.

## 6. Ejecución local (Fase 1 en adelante)

```bash
# backend
cd backend && set -a && source ../.env && set +a
./mvnw spring-boot:run                 # http://localhost:8095, OpenAPI UI at /api/docs

# frontend
cd frontend && npm ci && npm run dev   # http://localhost:5175 (proxies /api to :8095)

# tests
./mvnw test                            # unit tests, no database
./mvnw verify                          # + integration tests against jobhunter_test
npm test                               # vitest
```

## 7. Instalación en producción en Ubuntu (objetivo de la Fase 7)

1. Paquetes: `openjdk-21-jre-headless`, `mysql-server` (8.4 LTS mediante el repositorio APT de
   MySQL cuando se haga la actualización; mientras tanto 8.0.46), `nginx`, `certbot`. Node solo se
   necesita en la máquina de build.
2. Crear un usuario de sistema: `useradd --system --home /opt/jobhunter jobhunter`.
3. Copiar `jobhunter.jar` a `/opt/jobhunter/`. Poner el build de la SPA en `/var/www/jobhunter/`.
4. Los secretos van en `/etc/jobhunter/jobhunter.env` (propietario `root:jobhunter`, modo 640).
5. Agregar una unidad systemd `jobhunter.service`:
   - `ExecStart=/usr/bin/java -Xms256m -Xmx768m -jar /opt/jobhunter/jobhunter.jar`
   - `EnvironmentFile=/etc/jobhunter/jobhunter.env`, `User=jobhunter`
   - Endurecimiento: `NoNewPrivileges`, `ProtectSystem=strict`,
     `ReadWritePaths=/var/lib/jobhunter`.
6. nginx:
   - TLS
   - `location /api/ { proxy_pass http://127.0.0.1:8095; }`
   - Fallback de la SPA a `index.html`
   - `client_max_body_size 6m`
7. Respaldos: un cron nocturno ejecuta `mysqldump --single-transaction` y archiva el directorio de
   archivos; luego cifra ambos con `age` o `gpg`. Se conservan 14 días.
