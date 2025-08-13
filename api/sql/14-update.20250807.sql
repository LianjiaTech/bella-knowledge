-- 添加目录相关字段到file表
ALTER TABLE file
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_0
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_1
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_2
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_3
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_4
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_5
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_6
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_7
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_8
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_9
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_10
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_11
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_12
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_13
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_14
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;
ALTER TABLE file_15
    ADD COLUMN is_dir TINYINT(0) NOT NULL DEFAULT 0 COMMENT '是否为目录：1为目录，0为文件' AFTER filename,
    ADD INDEX idx_space_filename_status (`space_code`, `filename`, `status`) USING BTREE;

CREATE TABLE `file_closure`
(
    `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
    `ancestor_id`   VARCHAR(255)    NOT NULL COMMENT '祖先 file_id',
    `descendant_id` VARCHAR(255)    NOT NULL COMMENT '后代 file_id',
    `space_code`    varchar(128)    NOT NULL DEFAULT '' COMMENT '组织编码；对标OpenAI organization',
    `depth`         BIGINT          NOT NULL DEFAULT 0 COMMENT '深度',
    `root_depth`    BIGINT          NOT NULL DEFAULT -1 COMMENT '距离根目录的深度：1为根目录，2为第一级子文件...',
    `cuid`          bigint          NOT NULL DEFAULT 0,
    `cu_name`       varchar(32)     NOT NULL DEFAULT '',
    `ctime`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `muid`          bigint          NOT NULL DEFAULT 0,
    `mu_name`       varchar(32)     NOT NULL DEFAULT '',
    `mtime`         datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_ancestor_descendant` (`ancestor_id`, `descendant_id`) USING BTREE,
    INDEX idx_ancestor_depth_status (`ancestor_id`, `depth`) USING BTREE,
    INDEX idx_descendant_depth_status (`descendant_id`, `depth`) USING BTREE,
    INDEX idx_space_code_root_depth (`space_code`, `root_depth`) USING BTREE
)
    ENGINE = InnoDB
    AUTO_INCREMENT = 1
    DEFAULT CHARSET = utf8mb4
    COMMENT = '文件闭包表';

CREATE TABLE `file_closure_0` like `file_closure`;
CREATE TABLE `file_closure_1` like `file_closure`;
CREATE TABLE `file_closure_2` like `file_closure`;
CREATE TABLE `file_closure_3` like `file_closure`;
CREATE TABLE `file_closure_4` like `file_closure`;
CREATE TABLE `file_closure_5` like `file_closure`;
CREATE TABLE `file_closure_6` like `file_closure`;
CREATE TABLE `file_closure_7` like `file_closure`;
CREATE TABLE `file_closure_8` like `file_closure`;
CREATE TABLE `file_closure_9` like `file_closure`;
CREATE TABLE `file_closure_10` like `file_closure`;
CREATE TABLE `file_closure_11` like `file_closure`;
CREATE TABLE `file_closure_12` like `file_closure`;
CREATE TABLE `file_closure_13` like `file_closure`;
CREATE TABLE `file_closure_14` like `file_closure`;
CREATE TABLE `file_closure_15` like `file_closure`;

INSERT INTO file_closure_0 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_0
WHERE status = 0;
INSERT INTO file_closure_1 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_1
WHERE status = 0;
INSERT INTO file_closure_2 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_2
WHERE status = 0;
INSERT INTO file_closure_3 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_3
WHERE status = 0;
INSERT INTO file_closure_4 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_4
WHERE status = 0;
INSERT INTO file_closure_5 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_5
WHERE status = 0;
INSERT INTO file_closure_6 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_6
WHERE status = 0;
INSERT INTO file_closure_7 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_7
WHERE status = 0;
INSERT INTO file_closure_8 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_8
WHERE status = 0;
INSERT INTO file_closure_9 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_9
WHERE status = 0;
INSERT INTO file_closure_10 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_10
WHERE status = 0;
INSERT INTO file_closure_11 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_11
WHERE status = 0;
INSERT INTO file_closure_12 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_12
WHERE status = 0;
INSERT INTO file_closure_13 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_13
WHERE status = 0;
INSERT INTO file_closure_14 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_14
WHERE status = 0;
INSERT INTO file_closure_15 (ancestor_id, descendant_id, depth, root_depth, space_code)
SELECT file_id    AS ancestor_id,
       file_id    AS descendant_id,
       0          AS depth,
       1          AS root_depth,
       space_code AS space_code
FROM file_15
WHERE status = 0;
