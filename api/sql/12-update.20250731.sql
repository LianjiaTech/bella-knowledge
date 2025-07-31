alter table dataset_qa
    add column reasoning varchar(4096) not null default '' comment '推理过程/解题思路' AFTER answer,
    add column tags      varchar(8192) comment '标签信息冗余存储，格式：["tag1","tag2"]' AFTER reasoning;

-- 创建标签表
CREATE TABLE `bella_file_api`.`tag`
(
    `id`         bigint       NOT NULL AUTO_INCREMENT,
    `space_code` varchar(128) NOT NULL DEFAULT '' COMMENT '空间编码',
    `name`       varchar(100) NOT NULL DEFAULT '' COMMENT '标签名称',
    `cuid`       bigint       NOT NULL DEFAULT 0,
    `cu_name`    varchar(32)  NOT NULL DEFAULT '',
    `ctime`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`       bigint       NOT NULL DEFAULT 0,
    `mu_name`    varchar(32)  NOT NULL DEFAULT '',
    `mtime`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `status`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '状态：0正常，-1删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_space_name` (`space_code`, `name`) USING BTREE,
    INDEX `idx_ctime` (`ctime`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
    COMMENT ='标签定义表';
