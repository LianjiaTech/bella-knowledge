create table file_temp like file;
create table file_system like file;

CREATE TABLE `file_sharding`
(
    `id`        bigint unsigned NOT NULL AUTO_INCREMENT,
    `type`      varchar(32)     NOT NULl DEFAULT '' COMMENT '分片类型：temp-临时文件，system-系统文件',
    `key`       varchar(255)    NOT NULL DEFAULT '' COMMENT '分表的标识，',
    `key_time`  datetime        NOT NULL COMMENT '当前分片第一条数据的时间，用于索引分片',
    `last_key`  varchar(255)    NOT NULL DEFAULT '' COMMENT '上一次分表的标识，用于分表创建并发控制',
    `count`     bigint unsigned NOT NULL DEFAULT '0' COMMENT '分表的记录数量',
    `max_count` bigint unsigned NOT NULL DEFAULT '20000000' COMMENT '分表的最大记录数\n如果count>max_count， 创建新表',
    `ctime`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `cu_name`   varchar(64)     NOT NULL DEFAULT '',
    `cuid`      bigint          NOT NULL DEFAULT '0',
    `mtime`     datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `mu_name`   varchar(255)    NOT NULL DEFAULT '',
    `muid`      bigint          NOT NULL DEFAULT '0',
    PRIMARY KEY (`id`) USING BTREE,
    constraint idx_type_key
        unique (type, `key`),
    index idx_type_last_key (type, last_key) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4;

-- 初始化 file_sharding 表数据 (参考 dataset_sharding)
-- 初始化 system 类型分片 (使用无后缀的基础表 file_system)
insert into `file_sharding` (`type`, `key`, `key_time`, `last_key`, `count`, `max_count`, `cu_name`, `cuid`, `mu_name`,
                             `muid`)
values ('system', '', '2025-09-22 00:00:00', 'NO', 0, 20000000, 'system', 0, 'system', 0);

-- 初始化 temp 类型分片 (使用无后缀的基础表 file_temp)
insert into `file_sharding` (`type`, `key`, `key_time`, `last_key`, `count`, `max_count`, `cu_name`, `cuid`, `mu_name`,
                             `muid`)
values ('temp', '', '2025-09-22 00:00:00', 'NO', 0, 20000000, 'system', 0, 'system', 0);
