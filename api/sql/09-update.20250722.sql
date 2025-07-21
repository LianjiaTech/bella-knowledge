-- 简化的Dataset分片升级方案：单表+type字段
-- 使用事务保证数据一致性

START TRANSACTION;

-- 1. 删除原有的约束和索引（因为现在需要支持type字段）
ALTER TABLE `dataset_sharding`
    DROP INDEX `idx_key`;
ALTER TABLE `dataset_sharding`
    DROP INDEX `idx_last_key`;

-- 2. 添加type字段，默认为'qa'以兼容现有数据
ALTER TABLE `dataset_sharding`
    ADD COLUMN `type` varchar(32) NOT NULL DEFAULT 'qa' COMMENT '分片类型：qa-问答数据，document-文档数据' AFTER `id`;

-- 3. 重新创建索引（加上type字段支持）
CREATE UNIQUE INDEX `idx_type_key` ON `dataset_sharding` (`type`, `key`);
CREATE INDEX `idx_type_last_key` ON `dataset_sharding` (`type`, `last_key`);

-- 4. 创建document类型的分片记录
INSERT INTO `dataset_sharding` (`key`, `key_time`, `last_key`, `count`, `max_count`, `cu_name`, `cuid`, `mu_name`,
                                `muid`, `type`)
VALUES ('', '2025-06-18 00:00:00', 'NO', 0, 20000000, '', 0, '', 0, 'document');

COMMIT;
