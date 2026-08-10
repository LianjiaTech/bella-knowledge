ALTER TABLE `file`
    ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file' COMMENT '节点类型：file、directory、resource',
    ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '' COMMENT '业务资源标识';

ALTER TABLE `file_0` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_1` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_2` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_3` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_4` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_5` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_6` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_7` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_8` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_9` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_10` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_11` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_12` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_13` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_14` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_15` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_temp` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';
ALTER TABLE `file_system` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT 'file', ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT '';

-- key 为空的基础表已在上方迁移；历史 temp/system 文件仍会按 file_sharding 路由到已滚动分片，因此需要逐表补列。
DROP PROCEDURE IF EXISTS `migrate_file_resource_shards`;
DELIMITER //
CREATE PROCEDURE `migrate_file_resource_shards`()
BEGIN
    DECLARE done int DEFAULT 0;
    DECLARE shard_type varchar(32);
    DECLARE shard_key varchar(255);
    DECLARE shard_table varchar(512);
    DECLARE shard_cursor CURSOR FOR
        SELECT `type`, `key`
        FROM `file_sharding`
        WHERE `type` IN ('temp', 'system') AND `key` <> ''
        ORDER BY `id`;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN shard_cursor;
    shard_loop: LOOP
        FETCH shard_cursor INTO shard_type, shard_key;
        IF done = 1 THEN
            LEAVE shard_loop;
        END IF;

        SET shard_table = CONCAT('file_', shard_type, '_', shard_key);
        SET @alter_shard_sql = CONCAT(
                'ALTER TABLE `', REPLACE(shard_table, '`', '``'),
                '` ADD COLUMN `node_type` varchar(16) NOT NULL DEFAULT ', CHAR(39), 'file', CHAR(39), ',',
                ' ADD COLUMN `resource_id` varchar(256) NOT NULL DEFAULT ', CHAR(39), CHAR(39)
            );
        PREPARE alter_shard_stmt FROM @alter_shard_sql;
        EXECUTE alter_shard_stmt;
        DEALLOCATE PREPARE alter_shard_stmt;
    END LOOP;
    CLOSE shard_cursor;
END//
CALL `migrate_file_resource_shards`()//
DROP PROCEDURE `migrate_file_resource_shards`//
DELIMITER ;

UPDATE `file` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_0` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_1` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_2` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_3` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_4` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_5` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_6` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_7` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_8` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_9` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_10` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_11` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_12` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_13` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_14` SET `node_type` = 'directory' WHERE `is_dir` = 1;
UPDATE `file_15` SET `node_type` = 'directory' WHERE `is_dir` = 1;
