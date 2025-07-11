ALTER TABLE `bella_file_api`.`dataset`
    ADD COLUMN
        `latest_export_time`           datetime     NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '数据集最新导出时间',
    ADD COLUMN `latest_export_file_id` varchar(256) NOT NULL DEFAULT '' COMMENT '数据集最新导出文件ID';
