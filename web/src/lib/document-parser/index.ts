import {
  DocumentData,
  DocumentNode,
  DocumentElement,
} from "@/lib/types/documents";
import { Block } from "@blocknote/core";

function convertElementToBlock(
  element: DocumentElement,
  path: number[] = [],
): Block {
  const id = path.join("-");

  // 为所有Block添加位置信息到props中
  const baseProps = {
    path: path || [],
    positions: element.positions || [],
  };

  switch (element.type) {
    case "Title":
      return {
        id,
        type: "heading",
        props: {
          level: path.length as number,
          textColor: "default",
          backgroundColor: "default",
          textAlignment: "left",
          isToggleable: false,
          ...baseProps,
        },
        content: [
          {
            type: "text",
            text: element.text || "",
            styles: {},
          },
        ],
        children: [],
      } as unknown as Block;

    case "Text":
      return {
        id,
        type: "paragraph",
        props: {
          textColor: "default",
          backgroundColor: "default",
          textAlignment: "left",
          ...baseProps,
        },
        content: [
          {
            type: "text",
            text: element.text || "",
            styles: {},
          },
        ],
        children: [],
      } as unknown as Block;

    case "List":
    case "ListItem":
      return {
        id,
        type: "bulletListItem",
        props: {
          textColor: "default",
          backgroundColor: "default",
          textAlignment: "left",
          ...baseProps,
        },
        content: [
          {
            type: "text",
            text: element.text || "",
            styles: {},
          },
        ],
        children: [],
      } as unknown as Block;

    case "Code":
      return {
        id,
        type: "codeBlock",
        props: {
          language: "javascript",
          textColor: "default",
          backgroundColor: "default",
          ...baseProps,
        },
        content: [
          {
            type: "text",
            text: element.text || "",
            styles: {},
          },
        ],
        children: [],
      } as unknown as Block;

    case "Table":
      // 表格转换为BlockNote表格格式
      let tableContent;

      if (element.rows && element.rows.length > 0) {
        // 计算最大列数
        const maxCols = Math.max(
          ...element.rows.map((row) => row.cells.length),
        );
        const columnWidths = Array(maxCols).fill(200); // 默认每列200px

        // 创建空单元格的工厂函数
        const createEmptyCell = () => ({
          type: "tableCell",
          content: [
            {
              type: "text",
              text: "",
              styles: {},
            },
          ],
          props: {
            colspan: 1,
            rowspan: 1,
            backgroundColor: "default",
            textColor: "default",
            textAlignment: "left",
          },
        });

        // 将表格数据转换为BlockNote表格格式，并补全缺少的列
        const rows = element.rows.map((row) => {
          // 转换现有的单元格
          const cells = row.cells.map((cell) => ({
            type: "tableCell",
            content: [
              {
                type: "text",
                text: cell.text || "",
                styles: {},
              },
            ],
            props: {
              colspan: 1,
              rowspan: 1,
              backgroundColor: "default",
              textColor: "default",
              textAlignment: "left",
            },
          }));

          // 补全缺少的列（如果当前行的列数小于最大列数）
          while (cells.length < maxCols) {
            cells.push(createEmptyCell());
          }

          return { cells };
        });

        tableContent = {
          type: "tableContent",
          columnWidths,
          rows,
        };
      } else {
        // 如果没有表格数据，创建一个空的2x2表格
        tableContent = {
          type: "tableContent",
          columnWidths: [200, 200],
          rows: [
            {
              cells: [
                {
                  type: "tableCell",
                  content: [{ type: "text", text: "", styles: {} }],
                  props: {
                    colspan: 1,
                    rowspan: 1,
                    backgroundColor: "default",
                    textColor: "default",
                    textAlignment: "left",
                  },
                },
                {
                  type: "tableCell",
                  content: [{ type: "text", text: "", styles: {} }],
                  props: {
                    colspan: 1,
                    rowspan: 1,
                    backgroundColor: "default",
                    textColor: "default",
                    textAlignment: "left",
                  },
                },
              ],
            },
            {
              cells: [
                {
                  type: "tableCell",
                  content: [{ type: "text", text: "", styles: {} }],
                  props: {
                    colspan: 1,
                    rowspan: 1,
                    backgroundColor: "default",
                    textColor: "default",
                    textAlignment: "left",
                  },
                },
                {
                  type: "tableCell",
                  content: [{ type: "text", text: "", styles: {} }],
                  props: {
                    colspan: 1,
                    rowspan: 1,
                    backgroundColor: "default",
                    textColor: "default",
                    textAlignment: "left",
                  },
                },
              ],
            },
          ],
        };
      }

      return {
        id,
        type: "table",
        props: {
          textColor: "default" as const,
          backgroundColor: "default" as const,
          ...baseProps,
        },
        content: tableContent,
        children: [],
      } as unknown as Block;

    case "Figure":
      let imageUrl = "";
      const image = element.image!;
      if (image.type === "image_url") {
        imageUrl = image.url;
      } else if (image.type === "image_base64") {
        imageUrl = image.base64;
      }

      return {
        id,
        type: "image",
        props: {
          url: imageUrl,
          caption: element.text || "",
          textAlignment: "center" as const,
          backgroundColor: "default" as const,
          name: "",
          showPreview: true,
          previewWidth: 512,
          ...baseProps,
        },
        content: [],
        children: [],
      } as unknown as Block;

    case "Formula":
      return {
        id,
        type: "formula",
        props: {
          ...baseProps,
        },
        content: [
          {
            type: "text",
            text: element.text || "",
            styles: {},
          },
        ],
        children: [],
      } as unknown as Block;

    case "Catalog":
      return {
        id,
        type: "catalog",
        props: {
          ...baseProps,
        },
        content: [
          {
            type: "text",
            text: element.text || "",
          },
        ],
        children: [],
      } as unknown as Block;

    default:
      return {
        id,
        type: "paragraph",
        props: {
          ...baseProps,
        },
        content: [
          {
            type: "text",
            text: element.text || "",
          },
        ],
        children: [],
      } as unknown as Block;
  }
}

export function documentDataToBlocks(documentData: DocumentData): Block[] {
  if (!documentData) {
    return [
      {
        id: "",
        type: "paragraph",
        props: {
          textColor: "default",
          backgroundColor: "default",
          textAlignment: "left",
        },
        content: [],
        children: [],
      } as Block,
    ];
  }

  const blocks: Block[] = [];

  function convertNodeWithLevel(node: DocumentNode): Block[] {
    const nodeBlocks: Block[] = [];

    if (node.element) {
      // 跳过没有文本内容的Title节点
      if (node.element.type === "Title" && !node.element.text?.trim()) {
        // 只处理子节点，不转换当前节点
        if (node.children && node.children.length > 0) {
          node.children.forEach((child) => {
            const childBlocks = convertNodeWithLevel(child);
            nodeBlocks.push(...childBlocks);
          });
        }
        return nodeBlocks;
      }

      const converted = convertElementToBlock(node.element, node.path || []);

      // 如果是标题类型，根据层级调整
      if (converted.type === "heading" || node.element.type === "Title") {
        const level = node.path?.length || 1;
        const validLevel = Math.min(Math.max(level, 1), 6) as
          | 1
          | 2
          | 3
          | 4
          | 5
          | 6;
        converted.props = {
          ...converted.props,
          level: validLevel,
        };
      }

      nodeBlocks.push(converted);
    }

    // 递归处理子节点，层级+1
    if (node.children && node.children.length > 0) {
      node.children.forEach((child) => {
        const childBlocks = convertNodeWithLevel(child);
        nodeBlocks.push(...childBlocks);
      });
    }

    return nodeBlocks;
  }

  // 处理根元素
  if (documentData.element) {
    const converted = convertElementToBlock(documentData.element, []);
    blocks.push(converted);
  }

  // 处理子节点
  if (documentData.children && documentData.children.length > 0) {
    documentData.children.forEach((child) => {
      const childBlocks = convertNodeWithLevel(child);
      blocks.push(...childBlocks);
    });
  }

  return blocks.length > 0
    ? blocks
    : [
        {
          id: "",
          type: "paragraph",
          props: {
            textColor: "default",
            backgroundColor: "default",
            textAlignment: "left",
          },
          content: [],
          children: [],
        } as Block,
      ];
}

function convertBlockToElement(
  block: Block,
  originalData: DocumentData,
): DocumentElement {
  const textContent = extractTextFromBlockContent(block.content);

  let positions:
    | { bbox: [number, number, number, number]; page: number }[]
    | undefined;

  // 检查block是否有自定义的位置数据
  if (block.props && typeof block.props === "object") {
    const props = block.props as Record<string, unknown>;
    if (props.positions && Array.isArray(props.positions)) {
      positions = props.positions as {
        bbox: [number, number, number, number];
        page: number;
      }[];
    }
  }
  positions = mergePositions(positions, block, originalData);
  positions = positions || [];

  switch (block.type) {
    case "heading":
      return {
        type: "Title",
        text: textContent,
        positions: positions,
      };

    case "paragraph":
      return {
        type: "Text",
        text: textContent,
        positions: positions,
      };

    case "bulletListItem":
      return {
        type: "List",
        text: textContent,
        positions: positions,
      };

    case "codeBlock":
      // 根据语言判断是代码还是公式
      const blockProps = block.props as Record<string, unknown>;
      const language = (blockProps?.language as string) || "javascript";

      if (language === "latex") {
        return {
          type: "Formula",
          text: textContent,
          positions: positions,
        };
      }
      return {
        type: "Code",
        text: textContent,
        positions: positions,
      };

    case "image":
      const imageProps = block.props as Record<string, unknown>;
      const imageUrl = (imageProps?.url as string) || "";
      const caption = (imageProps?.caption as string) || "";

      return {
        type: "Figure",
        text: caption,
        image: {
          type: imageUrl.startsWith("data:") ? "image_base64" : "image_url",
          url: imageUrl.startsWith("data:") ? "" : imageUrl,
          base64: imageUrl.startsWith("data:") ? imageUrl : "",
          file_id: block.id || "",
        },
        positions: positions,
      };

    case "table":
      // 处理表格块
      if (
        block.content &&
        typeof block.content === "object" &&
        "type" in block.content &&
        block.content.type === "tableContent"
      ) {
        const tableContent = block.content as {
          type: "tableContent";
          columnWidths?: number[];
          rows: {
            cells: Array<{
              type: "tableCell";
              content: Array<{ type: string; text: string; styles: object }>;
              props: {
                colspan: number;
                rowspan: number;
                backgroundColor: string;
                textColor: string;
                textAlignment: string;
              };
            }>;
          }[];
        };

        const rows = tableContent.rows.map((row, rowIndex) => ({
          cells: row.cells.map((cell, cellIndex) => ({
            text: cell.content.length > 0 ? cell.content[0].text || "" : "",
            path: [rowIndex, rowIndex, cellIndex, cellIndex],
          })),
        }));

        return {
          type: "Table",
          text: "",
          rows: rows,
          positions: positions,
        };
      }

      // 如果表格内容格式不正确，返回空表格
      return {
        type: "Table",
        text: "",
        rows: [
          {
            cells: [
              { text: "", path: [0, 0, 0, 0] },
              { text: "", path: [0, 0, 1, 1] },
            ],
          },
        ],
        positions: positions,
      };
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    case "formula":
      return {
        type: "Formula",
        text: textContent,
        positions: positions,
      };

    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore

    case "catalog":
      return {
        type: "Catalog",
        text: textContent,
        positions: positions,
      };

    default:
      return {
        type: "Text",
        text: textContent,
        positions: positions,
      };
  }
}

export function blocksToDocumentData(
  blocks: Block[],
  originalData: DocumentData,
): DocumentData {
  if (!blocks || blocks.length === 0) {
    return originalData;
  }

  const root: DocumentData = {
    element: null,
    children: [],
    source_file: originalData.source_file,
    summary: originalData.summary,
    tokens: originalData.tokens,
    path: null,
  };

  // 用于跟踪当前每个层级的节点，索引对应层级-1
  const levelStack: DocumentNode[] = [];

  for (let i = 0; i < blocks.length; i++) {
    const block = blocks[i];
    const element = convertBlockToElement(block, originalData);

    // 创建新的文档节点
    const newNode: DocumentNode = {
      path: [], // 稍后计算
      element: element,
      children: [],
      summary: "",
      tokens: 0,
      source_file: null,
    };

    // 如果是标题类型，需要根据level构建层级结构
    if (block.type === "heading") {
      const blockProps = block.props as Record<string, unknown>;
      const level = (blockProps?.level as number) || 1;

      // 只在需要填充中间层级时创建空父节点
      // 如果当前层级栈不为空，且当前level比栈顶level大于1，则需要填充中间层级
      const needEmptyParents =
        levelStack.length > 0 && level > levelStack.length + 1;

      if (needEmptyParents) {
        while (levelStack.length < level - 1) {
          const missingLevel = levelStack.length + 1;

          // 创建一个空的Title父节点
          const parentNode: DocumentNode = {
            path: [], // 稍后计算
            element: {
              type: "Title",
              text: "",
              positions: [],
            },
            children: [],
            summary: "",
            tokens: 0,
            source_file: null,
          };

          // 添加到上一级的children中
          const parentLevel = missingLevel - 1;
          if (levelStack[parentLevel - 1]) {
            if (!levelStack[parentLevel - 1].children) {
              levelStack[parentLevel - 1].children = [];
            }
            levelStack[parentLevel - 1].children!.push(parentNode);
          }

          levelStack[missingLevel - 1] = parentNode;
        }
      }

      // 截断栈，移除当前level及之后的节点
      levelStack.splice(level);

      // 将当前节点添加到正确的父节点
      if (level === 1 || levelStack.length === 0) {
        // level 1 或者没有父节点时，直接添加到root
        if (!root.children) root.children = [];
        root.children.push(newNode);
      } else {
        // 添加到对应层级的父节点
        const parentLevel = level - 1;
        if (parentLevel <= levelStack.length && levelStack[parentLevel - 1]) {
          if (!levelStack[parentLevel - 1].children) {
            levelStack[parentLevel - 1].children = [];
          }
          levelStack[parentLevel - 1].children!.push(newNode);
        } else {
          // 如果没有对应的父节点，直接添加到root
          if (!root.children) root.children = [];
          root.children.push(newNode);
        }
      }

      // 更新栈
      levelStack[level - 1] = newNode;
    } else {
      // 非标题类型，添加到最近的父节点或root
      if (levelStack.length > 0) {
        // 添加到最后一个标题节点的children中
        const lastHeading = levelStack[levelStack.length - 1];
        if (!lastHeading.children) lastHeading.children = [];
        lastHeading.children.push(newNode);
      } else {
        // 没有标题节点，直接添加到root
        if (!root.children) root.children = [];
        root.children.push(newNode);
      }
    }
  }

  // 重新计算所有节点的path
  function recalculatePaths(
    node: DocumentData | DocumentNode,
    basePath: number[] = [],
  ): void {
    if ("children" in node && node.children) {
      node.children.forEach((child, index) => {
        const newPath = [...basePath, index + 1];
        child.path = newPath;
        recalculatePaths(child, newPath);
      });
    }
  }

  recalculatePaths(root);

  return root;
}

function extractTextFromBlockContent(content: unknown): string {
  if (!content || !Array.isArray(content)) return "";

  return content
    .map((item: unknown) => {
      if (typeof item === "string") return item;
      if (
        item &&
        typeof item === "object" &&
        "text" in item &&
        typeof (item as { text: unknown }).text === "string"
      ) {
        return (item as { text: string }).text;
      }
      return "";
    })
    .join("");
}

function mergePositions(
  blockPositions:
    | { bbox: [number, number, number, number]; page: number }[]
    | undefined,
  block: Block,
  originalData: DocumentData,
): { bbox: [number, number, number, number]; page: number }[] {
  const findNodeByPath = (
    nodes: DocumentNode[],
    blockId: string,
  ): DocumentNode | null => {
    const path = blockId.split("-").map(Number);
    const isValidPath = path.every((v) => v >= 0);

    if (!isValidPath) {
      return null;
    }
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (node.path && node.path.join("-") === blockId) {
        return node;
      }
      if (node.children) {
        const found = findNodeByPath(node.children, blockId);
        if (found) {
          return found;
        }
      }
    }
    return null;
  };
  if (Array.isArray(blockPositions)) {
    return blockPositions;
  }
  const originalNode = findNodeByPath(originalData.children || [], block.id);
  if (originalNode && originalNode.element.positions) {
    return originalNode.element.positions;
  }
  return originalNode?.element.positions || [];
}
