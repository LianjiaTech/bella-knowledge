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
