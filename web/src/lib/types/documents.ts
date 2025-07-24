export interface DocumentElement {
  type:
    | "Catalog"
    | "Figure"
    | "Table"
    | "Title"
    | "List"
    | "Text"
    | "Code"
    | "ListItem"
    | "Formula";
  positions: { bbox: [number, number, number, number]; page: number }[];
  text?: string;
  image?: {
    type: "image_base64" | "image_url" | "image_file";
    base64: string;
    url: string;
    file_id: string;
  };
  rows?: {
    cells: {
      text: string;
      path: number[]; // 单元格在表格中的位置 start row, end row, start column, end column
      nodes?: DocumentNode[];
    }[];
  }[];
  name?: string;
  description?: string;
}

export interface DocumentNode {
  children?: DocumentNode[];
  element: DocumentElement;
  path: number[];
  summary: string;
  tokens: number;
  source_file: null;
}

export interface DocumentData {
  source_file: {
    id: string;
    name: string;
    type: string;
    mime_type: string;
  };
  summary: string;
  tokens: number;
  path: null;
  children?: DocumentNode[];
  element: DocumentElement | null;
}
