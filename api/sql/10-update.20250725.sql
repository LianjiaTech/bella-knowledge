alter table dataset_qa_reference
    add column snippet varchar(64) not null default '' comment 'snippet for dataset qa reference' after path;
