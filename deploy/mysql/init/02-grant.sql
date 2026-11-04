-- Executed by the MySQL entrypoint on first initialization (root user).
-- 02: keep the application account name/privileges explicit for the default
-- database name icp_platform. When MYSQL_DATABASE / APP_DB_USER are changed in
-- .env, the official mysql image already creates the account with grants on the
-- database, so this script mainly documents/repairs the expected privilege set.
CREATE USER IF NOT EXISTS 'icp_app'@'%' IDENTIFIED BY 'Icp@2026';
GRANT ALL PRIVILEGES ON icp_platform.* TO 'icp_app'@'%';
FLUSH PRIVILEGES;
