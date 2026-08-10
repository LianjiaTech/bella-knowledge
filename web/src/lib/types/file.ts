export type KnowledgeFile = {
  id: string;
  object: string;
  bytes: number;
  created_at: number;
  filename: string;
  purpose: string;
  type: string;
  extension: string;
  dom_tree_file_id: string;
  mime_type: string;
  is_dir: boolean;
  node_type: "file" | "directory" | "resource";
  resource_id: string;
};

export interface DatasetFile {
  id: string;
  file_id: string;
}
