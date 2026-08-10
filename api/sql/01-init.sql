-- ----------------------------
-- Table structure for file
-- ----------------------------
CREATE TABLE `file`
(
    `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
    `file_id`    varchar(256)    NOT NULL DEFAULT '' COMMENT '文件ID',
    `filename`   varchar(512)    NOT NULL DEFAULT '' COMMENT '文件名',
    `bucket`     varchar(256)    NOT NULL DEFAULT '' COMMENT '存储的地方',
    `path`       varchar(512)    NOT NULL DEFAULT '' COMMENT '文件路径',
    `bytes`      bigint          NOT NULL DEFAULT 0 COMMENT '文件大小；单位：字节',
    `space_code` varchar(128)    NOT NULL DEFAULT '' COMMENT '组织编码；对标OpenAI organization',
    `purpose`    varchar(64)     NOT NULL DEFAULT '' COMMENT '文件目的，参考OpenAI',
    `cuid`       bigint          NOT NULL DEFAULT 0,
    `cu_name`    varchar(32)     NOT NULL DEFAULT '',
    `ctime`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`       bigint          NOT NULL DEFAULT 0,
    `mu_name`    varchar(32)     NOT NULL DEFAULT '',
    `mtime`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `meta_data`  text,
    `status`     tinyint(1)      NOT NULL DEFAULT 0 COMMENT '文件是否被删除，0表示未删除，-1表示已删除',
    `ak_code`    varchar(128)    NOT NULL DEFAULT '' COMMENT '',
    `broadcast_status` bigint    NOT NULL DEFAULT 0 COMMENT '文件是否被广播成功，0表示广播失败，1表示广播成功',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_file_space` (`file_id`(80), `space_code`(80)) USING BTREE,
    INDEX `idx_ctime` (`ctime`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4;

-- ----------------------------
-- Table structure for file_mapping
-- ----------------------------
CREATE TABLE `file_mapping`
(
    `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
    `file_id`     varchar(256) NOT NULL DEFAULT '' COMMENT '',
    `file_id_old` varchar(256) NOT NULL DEFAULT '' COMMENT '老的file_id',
    `cuid`        bigint       NOT NULL DEFAULT 0,
    `cu_name`     varchar(32)  NOT NULL DEFAULT '',
    `ctime`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`        bigint       NOT NULL DEFAULT 0,
    `mu_name`     varchar(32)  NOT NULL DEFAULT '',
    `mtime`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_file_id_new_old` (`file_id_old`(80), `file_id`(80)) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4;

-- ----------------------------
-- Table structure for file_progress
-- ----------------------------
CREATE TABLE `file_progress`
(
    `id`      bigint unsigned NOT NULL AUTO_INCREMENT,
    `file_id` varchar(256)    NOT NULL DEFAULT '' COMMENT '文件id',
    `name`    varchar(256)    NOT NULL DEFAULT '' COMMENT '后处理名，一期由服务端枚举指定',
    `status`  varchar(256)    NOT NULL DEFAULT '' COMMENT '自定义状态，file api维护',
    `message` varchar(1024)   NOT NULL DEFAULT '' COMMENT '自定义信息，后处理维护',
    `percent` int(4)          NOT NULL DEFAULT 0 COMMENT '进度,单位百分比,0~100，后处理维护',
    `cuid`    bigint          NOT NULL DEFAULT 0,
    `cu_name` varchar(32)     NOT NULL DEFAULT '',
    `ctime`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`    bigint          NOT NULL DEFAULT 0,
    `mu_name` varchar(32)     NOT NULL DEFAULT '',
    `mtime`   datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_file_id_name` (`file_id`(80), `name`(80)) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `file_entry`
(
    `id`              bigint unsigned NOT NULL AUTO_INCREMENT,
    `entry_id`        varchar(96)      NOT NULL DEFAULT '',
    `space_code`      varchar(128)     NOT NULL DEFAULT '',
    `parent_entry_id` varchar(96)      NOT NULL DEFAULT '',
    `file_id`         varchar(256)     NOT NULL DEFAULT '',
    `filename`        varchar(512)     NOT NULL DEFAULT '',
    `type`            varchar(16)      NOT NULL DEFAULT 'file',
    `cuid`            bigint           NOT NULL DEFAULT 0,
    `cu_name`         varchar(32)      NOT NULL DEFAULT '',
    `ctime`           datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`            bigint           NOT NULL DEFAULT 0,
    `mu_name`         varchar(32)      NOT NULL DEFAULT '',
    `mtime`           datetime         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_entry` (`space_code`, `entry_id`),
    UNIQUE KEY `uk_space_parent_name` (`space_code`, `parent_entry_id`, `filename`),
    UNIQUE KEY `uk_space_file` (`space_code`, `file_id`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_parent_entry_id` (`parent_entry_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ----------------------------
-- Table structure for instance
-- ----------------------------
CREATE TABLE `instance`
(
    `id`     bigint unsigned NOT NULL AUTO_INCREMENT,
    `ip`     varchar(64) NOT NULL DEFAULT '',
    `port`   int         NOT NULL DEFAULT 0,
    `status` int         NOT NULL DEFAULT 0,
    `ctime`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mtime`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ip_port` (`ip`,`port`)
) ENGINE=InnoDB
	AUTO_INCREMENT=19
	DEFAULT CHARSET = utf8mb4;


create TABLE `file_0` like `file`;
create TABLE `file_1` like `file`;
create TABLE `file_2` like `file`;
create TABLE `file_3` like `file`;
create TABLE `file_4` like `file`;
create TABLE `file_5` like `file`;
create TABLE `file_6` like `file`;
create TABLE `file_7` like `file`;
create TABLE `file_8` like `file`;
create TABLE `file_9` like `file`;
create TABLE `file_10` like `file`;
create TABLE `file_11` like `file`;
create TABLE `file_12` like `file`;
create TABLE `file_13` like `file`;
create TABLE `file_14` like `file`;
create TABLE `file_15` like `file`;
create TABLE `file_entry_0` like `file_entry`;
create TABLE `file_entry_1` like `file_entry`;
create TABLE `file_entry_2` like `file_entry`;
create TABLE `file_entry_3` like `file_entry`;
create TABLE `file_entry_4` like `file_entry`;
create TABLE `file_entry_5` like `file_entry`;
create TABLE `file_entry_6` like `file_entry`;
create TABLE `file_entry_7` like `file_entry`;
create TABLE `file_entry_8` like `file_entry`;
create TABLE `file_entry_9` like `file_entry`;
create TABLE `file_entry_10` like `file_entry`;
create TABLE `file_entry_11` like `file_entry`;
create TABLE `file_entry_12` like `file_entry`;
create TABLE `file_entry_13` like `file_entry`;
create TABLE `file_entry_14` like `file_entry`;
create TABLE `file_entry_15` like `file_entry`;
