CREATE TABLE `bella_file_api`.`dataset_document`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT,
    `dataset_sharding_key` varchar(256) NOT NULL DEFAULT '' COMMENT '数据集分片的key',
    `dataset_id`           varchar(256) NOT NULL DEFAULT '' COMMENT '数据集ID',
    `file_id`              varchar(256) NOT NULL DEFAULT '' COMMENT '文件ID，作为document的主键',
    `cuid`                 bigint       NOT NULL DEFAULT 0,
    `cu_name`              varchar(32)  NOT NULL DEFAULT '',
    `ctime`                datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`                 bigint       NOT NULL DEFAULT 0,
    `mu_name`              varchar(32)  NOT NULL DEFAULT '',
    `mtime`                datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `status`               tinyint(1)   NOT NULL DEFAULT 0 COMMENT '文档是否被删除，0表示未删除，-1表示已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_dataset_file` (`dataset_id`, `file_id`) USING BTREE,
    INDEX `idx_dataset_id` (`dataset_id`) USING BTREE,
    INDEX `idx_file_id` (`file_id`) USING BTREE,
    INDEX `idx_ctime` (`ctime`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
    COMMENT ='文档数据集表';
