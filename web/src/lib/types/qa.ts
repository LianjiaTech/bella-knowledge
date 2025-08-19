export type Dataset = {
  id: number;
  dataset_id: string;
  space_code: string;
  name: string;
  type: string;
  remark: string;
  cuid: number;
  cu_name: string;
  ctime: string;
  muid: number;
  mu_name: string;
  mtime: string;
  status: number;
};

export type Question = {
  id: number;
  item_id: string;
  dataset_sharding_key: string;
  dataset_id: string;
  question: string;
  answer: string;
  cuid: number;
  cu_name: string;
  ctime: string;
  muid: number;
  mu_name: string;
  mtime: string;
  status: number;
  tags?: string[];
  reasoning?: string;
  scoring_criteria?: string;
};

export type QuestionList = Question[];

export type QaReference = {
  item_id: string;
  references: {
    file_id: string;
    path: number[];
    reference_id: number;
    snippet: string;
  }[];
};

export type QaReferenceList = QaReference[];
