ALTER TABLE `dataset_qa_reference`
ADD COLUMN `primary` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为关键知识，0表示普通知识，1表示关键知识'
AFTER `snippet`;
