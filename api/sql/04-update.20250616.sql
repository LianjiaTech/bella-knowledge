CREATE TABLE `bella_file_api`.`dataset`
(
    `id`         bigint       NOT NULL AUTO_INCREMENT,
    `space_code` varchar(128) NOT NULL DEFAULT '' COMMENT '组织编码；对标OpenAI organization',
    `dataset_id` varchar(256) NOT NULL DEFAULT '' COMMENT '数据集ID',
    `name`       varchar(128) NOT NULL DEFAULT '' COMMENT '数据集名称',
    `type`       varchar(32)  NOT NULL DEFAULT '' COMMENT '类型；QA：QA类型',
    `remark`     varchar(128) NOT NULL DEFAULT '' COMMENT '备注',
    `cuid`       bigint       NOT NULL DEFAULT 0,
    `cu_name`    varchar(32)  NOT NULL DEFAULT '',
    `ctime`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`       bigint       NOT NULL DEFAULT 0,
    `mu_name`    varchar(32)  NOT NULL DEFAULT '',
    `mtime`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `status`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '数据集是否被删除，0表示未删除，-1表示已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_dataset_id` (`dataset_id`) USING BTREE,
    INDEX `idx_space_code` (`space_code`) USING BTREE,
    INDEX `idx_ctime` (`ctime`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
    COMMENT ='数据集';

CREATE TABLE `bella_file_api`.`dataset_qa`
(
    `id`         bigint       NOT NULL AUTO_INCREMENT,
    `item_id`    varchar(256) NOT NULL DEFAULT '' COMMENT 'QA的ID',
    `dataset_id` varchar(256) NOT NULL DEFAULT '' COMMENT '数据集ID',
    `question`   longtext COMMENT '问题',
    `similar_q1` longtext COMMENT '相似问1',
    `similar_q2` longtext COMMENT '相似问2',
    `similar_q3` longtext COMMENT '相似问3',
    `answer`     longtext COMMENT '答案',
    `cuid`       bigint       NOT NULL DEFAULT 0,
    `cu_name`    varchar(32)  NOT NULL DEFAULT '',
    `ctime`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`       bigint       NOT NULL DEFAULT 0,
    `mu_name`    varchar(32)  NOT NULL DEFAULT '',
    `mtime`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `status`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '数据集是否被删除，0表示未删除，-1表示已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_item_id` (`item_id`) USING BTREE,
    INDEX `idx_dataset_item` (`dataset_id`, `item_id`) USING BTREE,
    INDEX `idx_ctime` (`ctime`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
    COMMENT ='问答数据集表';

CREATE TABLE `bella_file_api`.`dataset_qa_reference`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT,
    `reference_id` varchar(256) NOT NULL DEFAULT '' COMMENT '引用ID；由item_id/file_id/path确定',
    `item_id`      varchar(256) NOT NULL DEFAULT '' COMMENT '问答对ID',
    `dataset_id`   varchar(256) NOT NULL COMMENT '数据集ID',
    `file_id`      varchar(256) NOT NULL DEFAULT '' COMMENT '文件ID',
    `path`         varchar(512) NOT NULL DEFAULT '' COMMENT '位置信息',
    `cuid`         bigint       NOT NULL DEFAULT 0,
    `cu_name`      varchar(32)  NOT NULL DEFAULT '',
    `ctime`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`         bigint       NOT NULL DEFAULT 0,
    `mu_name`      varchar(32)  NOT NULL DEFAULT '',
    `mtime`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `status`       tinyint(1)   NOT NULL DEFAULT 0 COMMENT '引用是否被删除，0表示未删除，-1表示已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_reference_id` (`reference_id`) USING BTREE,
    INDEX `idx_dataset_id` (`dataset_id`) USING BTREE,
    INDEX `idx_item_id` (`item_id`) USING BTREE,
    INDEX `idx_ctime` (`ctime`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
    COMMENT ='数据集问答对引用关系表';

ALTER TABLE `bella_file_api`.`file`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_0`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_1`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_2`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_3`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_4`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_5`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_6`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_7`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_8`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_9`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_10`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_11`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_12`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_13`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_14`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

ALTER TABLE `bella_file_api`.`file_15`
    ADD COLUMN `dom_tree_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT 'DOM tree的文件ID';

