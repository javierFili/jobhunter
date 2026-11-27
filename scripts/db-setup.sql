-- JobHunter AI: crea los esquemas y usuarios de MySQL (ejecutar UNA vez como administrador).
-- Uso:  mysql -u root -p < scripts/db-setup.sql   (o "sudo mysql < ..." si root usa auth_socket)
-- Las contraseñas de abajo son solo para desarrollo local; cámbialas en producción
-- y refleja el cambio en el archivo .env.

CREATE DATABASE IF NOT EXISTS jobhunter      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS jobhunter_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'jobhunter'@'localhost'      IDENTIFIED BY 'JhDev#2026pw';
CREATE USER IF NOT EXISTS 'jobhunter_test'@'localhost' IDENTIFIED BY 'JhTest#2026pw';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
  ON jobhunter.* TO 'jobhunter'@'localhost';
GRANT ALL PRIVILEGES ON jobhunter_test.* TO 'jobhunter_test'@'localhost';
FLUSH PRIVILEGES;
