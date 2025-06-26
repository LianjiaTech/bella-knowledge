ALTER TABLE `bella_file_api`.`dataset`
    ADD INDEX `idx_mtime` (`mtime`) USING BTREE;

ALTER TABLE `bella_file_api`.`dataset_qa`
    ADD INDEX `idx_mtime` (`mtime`) USING BTREE;

ALTER TABLE `bella_file_api`.`dataset_qa_reference`
    ADD INDEX `idx_mtime` (`mtime`) USING BTREE;
