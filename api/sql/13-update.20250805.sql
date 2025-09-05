UPDATE dataset_qa_reference
SET path = CONCAT('/', REPLACE(path, ',', '/'));
