-- Executed by the MySQL entrypoint on first initialization (root user).
-- 01: utf8mb4 charset + Asia/Shanghai time zone to match the local instance.
SET NAMES utf8mb4;
SET GLOBAL time_zone = '+08:00';
SET time_zone = '+08:00';

-- The database name must match MYSQL_DATABASE (.env, default icp_platform).
ALTER DATABASE icp_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
