-- Covering index for the count endpoint's whole-space query:
--   select type, count(*) ... where space_code = ? group by type
-- Per-directory counts are already served by uk_space_parent_name.

ALTER TABLE `file_entry` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_0` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_1` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_2` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_3` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_4` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_5` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_6` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_7` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_8` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_9` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_10` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_11` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_12` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_13` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_14` ADD INDEX `idx_space_type` (`space_code`, `type`);
ALTER TABLE `file_entry_15` ADD INDEX `idx_space_type` (`space_code`, `type`);
