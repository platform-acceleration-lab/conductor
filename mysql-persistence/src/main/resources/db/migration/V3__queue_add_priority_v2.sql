SET @dbname = DATABASE();
SET @tablename = "queue_message";
SET @columnname = "priority";
SET @preparedStatement = (SELECT IF(
                                     (
                                         SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                                         WHERE
                                             (table_name = @tablename)
                                           AND (table_schema = @dbname)
                                           AND (column_name = @columnname)
                                     ) > 0,
                                     "SELECT 1",
                                     CONCAT("ALTER TABLE ", @tablename, " ADD COLUMN ", @columnname, " TINYINT DEFAULT 0 AFTER `message_id`;")
                                   )
                         );
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
