CREATE TABLE IF NOT EXISTS `file_entry`
(
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
    `entry_id`        varchar(96)      NOT NULL DEFAULT '',
    `space_code`      varchar(128)     NOT NULL DEFAULT '',
    `parent_entry_id` varchar(96)      NOT NULL DEFAULT '',
    `file_id`         varchar(256)     NOT NULL DEFAULT '',
    `filename`        varchar(512)     NOT NULL DEFAULT '',
    `type`            varchar(16)      NOT NULL DEFAULT 'file',
    `status`          tinyint(1)       NOT NULL DEFAULT 0,
    `active_flag`     tinyint(1) GENERATED ALWAYS AS (IF(`status` = 0, 1, NULL)) STORED,
    `cuid`            bigint           NOT NULL DEFAULT 0,
    `cu_name`         varchar(32)      NOT NULL DEFAULT '',
    `ctime`           datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`            bigint           NOT NULL DEFAULT 0,
    `mu_name`         varchar(32)      NOT NULL DEFAULT '',
    `mtime`           datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_entry` (`space_code`, `entry_id`),
    UNIQUE KEY `uk_space_parent_name_active` (`space_code`, `parent_entry_id`, `filename`, `active_flag`),
    KEY `idx_space_parent_status` (`space_code`, `parent_entry_id`, `status`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_parent_entry_id` (`parent_entry_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `file_entry_0` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_1` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_2` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_3` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_4` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_5` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_6` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_7` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_8` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_9` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_10` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_11` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_12` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_13` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_14` LIKE `file_entry`;
CREATE TABLE IF NOT EXISTS `file_entry_15` LIKE `file_entry`;
