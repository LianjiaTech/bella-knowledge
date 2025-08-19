-- 添加得分要点字段到dataset_qa表
ALTER TABLE dataset_qa
    ADD COLUMN scoring_criteria varchar(2048) COMMENT '评测集答案的评分依据/得分要点' AFTER reasoning;
