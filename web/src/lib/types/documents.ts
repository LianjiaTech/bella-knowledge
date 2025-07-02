export interface DocumentElement {
  type:
    | "Catalog"
    | "Figure"
    | "Table"
    | "TableName"
    | "FigureName"
    | "Title"
    | "List"
    | "Text"
    | "Code"
    | "ListItem"
    | "Formula";

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
    }[];
  }[];
}

export interface DocumentNode {
  children?: DocumentNode[];
  element: DocumentElement;
  path: number[];
}

export interface DocumentData {
  children?: DocumentNode[];
  element: DocumentElement | null;
}
