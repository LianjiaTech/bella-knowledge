export interface RagevalData {
  question: string;
  /**
   * 正确召回数
   */
  eval_gb_count: number;
  /**
   * 错误召回数
   */
  eval_incorrect_count: number;
  /**
   * 错误召回的引用
   */
  eval_incorrect_references: {
    file_id: string;
    path: string;
  }[];
  /**
   * 没用
   */
  eval_missed_count: number;
  /**
   * 没用
   */
  eval_missed_references: {
    file_id: string;
    path: string;
  }[];
  /**
   * 没用
   */
  eval_precision: number;
  /**
   * 没用
   */
  eval_recall: number;
  /**
   * 没用
   */
  eval_retrieved_count: number;
  /**
   * 期望召回
   */
  gb_references: {
    file_id: string;
    path: string;
  }[];
  generation_time: number;
  /**
   * 期望答案
   */
  groundtruth: string;
  /**
   * 全部召回的引用
   */
  reference: {
    metadata: {
      chunk_type: string;
      enhanced: false;
      entity_id: string;
      file_id: string;
      path: string;
      paths: string[];
      tokens: number;
    };
    score: number;
    text: string;
  }[];
  /**
   * 实际回答
   */
  response: string;
  retrieval_time: number;
  timestamp: string;
}
