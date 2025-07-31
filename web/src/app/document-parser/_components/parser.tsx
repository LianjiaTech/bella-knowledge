/* eslint-disable @typescript-eslint/ban-ts-comment */
"use client";
import React, { useEffect, useMemo, useState } from "react";
import {
  DefaultReactSuggestionItem,
  DragHandleButton,
  getDefaultReactSlashMenuItems,
  RemoveBlockItem,
  SideMenu,
  SideMenuController,
  SuggestionMenuController,
  useCreateBlockNote,
  createReactBlockSpec,
  SuggestionMenuProps,
} from "@blocknote/react";
import { zh } from "@blocknote/core/locales";
import { BlockNoteView } from "@blocknote/mantine";
import { DocumentNode } from "@/lib/types/documents";
import { useThrottleFn } from "ahooks";
import {
  blocksToDocumentData,
  documentDataToBlocks,
} from "@/lib/document-parser";
import { useDocumentParserStore, store } from "../model";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import {
  Block,
  BlockNoteEditor,
  filterSuggestionItems,
  BlockNoteSchema,
  defaultBlockSpecs,
  defaultProps,
  insertOrUpdateBlock,
} from "@blocknote/core";
import "@blocknote/mantine/style.css";
import "@blocknote/core/fonts/inter.css";

import {
  List,
  Calculator,
  Trash,
  Heading1,
  Heading2,
  Heading3,
  Heading4,
  Heading5,
  Heading6,
  Text,
  Code,
  Table as TableIcon,
  Radical,
  Image as ImageIcon,
  NotebookText,
} from "lucide-react";
import ResetBlockTypeItems from "./reset-block-type-item";
import { toast } from "sonner";
import { SideMenuIcon } from "./side-menu-icon";
import { Spinner } from "@/components/ui/spinner";
import { cn } from "@/lib/utils";
import { useLocalState } from "@/hooks/use-local-state";

// 自定义块类型定义
const CatalogBlock = createReactBlockSpec(
  {
    type: "catalog",
    propSchema: {
      textAlignment: defaultProps.textAlignment,
      textColor: defaultProps.textColor,
    },
    content: "inline",
  },
  {
    render: (props) => {
      return (
        <div className="text-gray-900 font-semibold m-0 text-base">
          <div ref={props.contentRef} />
        </div>
      );
    },
  },
);

const FormulaBlock = createReactBlockSpec(
  {
    type: "formula",
    propSchema: {
      textAlignment: defaultProps.textAlignment,
      textColor: defaultProps.textColor,
    },
    content: "inline",
  },
  {
    render: (props) => {
      return (
        <div className="formula-block border border-purple-300 bg-purple-50 p-4 rounded-md">
          <div className="flex items-center gap-2 mb-2">
            <Calculator className="w-4 h-4 text-purple-600" />
            <span className="text-xs text-purple-600 font-medium">
              数学公式
            </span>
          </div>
          <div className="text-center font-mono text-lg text-gray-900 bg-white p-3 rounded border">
            <div ref={props.contentRef} />
          </div>
        </div>
      );
    },
  },
);

// 创建自定义schema
const customSchema = BlockNoteSchema.create({
  blockSpecs: {
    ...defaultBlockSpecs,
    catalog: CatalogBlock,
    formula: FormulaBlock,
  },
});

interface OutlinePanelProps {
  onNavigateToNode: (node: DocumentNode) => void;
  highlightedPath?: number[] | null;
}

// 递归生成目录项的函数
const generateOutlineItems = (
  nodes: DocumentNode[] | undefined,
  level: number = 0,
  onNavigateToNode: (node: DocumentNode) => void,
  highlightedPath?: number[] | null,
): React.ReactElement[] => {
  if (!nodes) return [];

  const items: React.ReactElement[] = [];

  nodes.forEach((node) => {
    const isTitle = node.element?.type === "Title";
    const text = node.element?.text || "";

    if (isTitle && text) {
      const handleClick = () => {
        if (node.path) {
          onNavigateToNode(node);
        }
      };

      let isHighlighted = false;
      const getIsHighlighted = (node: DocumentNode) => {
        if (highlightedPath) {
          if (
            highlightedPath.join("-") === node.path.join("-") &&
            node.element?.type === "Title"
          ) {
            return true;
          }
          for (const child of node.children || []) {
            if (
              highlightedPath.join("-") === child.path.join("-") &&
              child.element?.type !== "Title"
            ) {
              return true;
            }
          }
        }
        return false;
      };
      isHighlighted = getIsHighlighted(node);

      items.push(
        <div
          key={node.path.join("-")}
          className="outline-item"
          data-path={node.path.join("-")}
        >
          <div
            className={`
              cursor-pointer hover:bg-gray-100 rounded px-2 py-1 text-sm
              ${isTitle ? "font-semibold text-gray-900" : "text-gray-700"}
              ${isHighlighted ? "bg-blue-100 border-l-2 border-blue-500 font-medium" : ""}
            `}
            style={{ paddingLeft: `${level * 12 + 8}px` }}
            onClick={handleClick}
          >
            <span className="truncate block" title={text}>
              {text || `${node.element?.type || "Item"}`}
            </span>
          </div>
        </div>,
      );
    }

    // 递归处理子节点
    if (node.children) {
      items.push(
        ...generateOutlineItems(
          node.children,
          level + 1,
          onNavigateToNode,
          highlightedPath,
        ),
      );
    }
  });

  return items;
};

const OutlinePanel: React.FC<OutlinePanelProps> = ({
  onNavigateToNode,
  highlightedPath,
}) => {
  const { editingDomData } = useDocumentParserStore();
  const [open, setOpen] = useState(false);
  const outlineItems = useMemo(() => {
    if (!editingDomData) return [];
    return generateOutlineItems(
      editingDomData.children,
      0,
      onNavigateToNode,
      highlightedPath,
    );
  }, [editingDomData, onNavigateToNode, highlightedPath]);
  if (!editingDomData) {
    return (
      <div className="w-1/4 p-4 border-r">
        <div className="text-sm text-gray-500">暂无文档结构</div>
      </div>
    );
  }

  return (
    <div
      className={`border-r flex flex-col transition-all duration-300 ${
        open ? "w-1/4 p-4" : "p-2"
      }`}
    >
      {!open && (
        <List
          className="mt-1 size-4 cursor-pointer"
          onClick={() => setOpen(!open)}
        />
      )}
      <div
        className={`${open ? "block" : "hidden"} flex flex-col overflow-hidden`}
      >
        <div className="flex items-center justify-between text-sm font-medium text-gray-900 mb-3">
          <span>文档目录</span>
          <List
            className="size-4 cursor-pointer"
            onClick={() => setOpen(!open)}
          />
        </div>
        <div className="flex-1 overflow-y-auto">
          {outlineItems.length > 0 ? (
            outlineItems
          ) : (
            <div className="text-sm text-gray-400">无目录项</div>
          )}
        </div>
      </div>
    </div>
  );
};

interface RightPanelProps {
  width: number;
}

const RightPanel: React.FC<RightPanelProps> = ({ width }) => {
  const {
    currentEditingBlock,
    allBlockPositions,
    removePosition,
    updatePositionPage,
    saveDomData,
    recordAction,
  } = useDocumentParserStore();
  const currentEditingPositions = useMemo(() => {
    if (!currentEditingBlock) return [];
    return allBlockPositions[currentEditingBlock.id] || [];
  }, [currentEditingBlock, allBlockPositions]);
  return (
    <div
      className="p-4 border-l overflow-hidden flex flex-col"
      style={{ width: `${width}%` }}
    >
      <div className="flex items-center justify-between mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-sm font-medium text-gray-900">原文位置标注</div>
          <div className="text-xs text-gray-500">
            添加选区：在原始文件中框选对应片段
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {currentEditingPositions && currentEditingPositions.length > 0 ? (
          <div className="border rounded-lg overflow-hidden">
            <Table className="text-xs">
              <TableHeader>
                <TableRow className="bg-gray-50">
                  <TableHead className="text-gray-900 font-medium">
                    序号
                  </TableHead>
                  <TableHead className="text-gray-900 font-medium">
                    页码
                  </TableHead>
                  <TableHead className="text-gray-900 font-medium">
                    坐标
                  </TableHead>
                  <TableHead className="text-gray-900 font-medium">
                    操作
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {currentEditingPositions.map((position, index) => (
                  <TableRow key={position.id}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <div
                          className={`w-3 h-3 rounded border-2 ${
                            position.type === "manual"
                              ? "bg-blue-100 border-blue-500"
                              : "bg-red-100 border-red-500"
                          }`}
                        />
                        <span className="font-medium">#{index + 1}</span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <input
                        type="number"
                        min="1"
                        value={position.page + 1}
                        onChange={(e) => {
                          const newPage = parseInt(e.target.value) - 1;
                          if (newPage >= 0) {
                            updatePositionPage(position.id, newPage);
                          }
                        }}
                        className="w-12 px-1 py-0 text-xs border border-gray-300 rounded focus:outline-none focus:ring-1 focus:ring-blue-500"
                      />
                    </TableCell>
                    <TableCell className="text-gray-600">
                      <div className="space-y-1">
                        <div>
                          ({position.bbox[0].toFixed(1)},{" "}
                          {position.bbox[1].toFixed(1)})
                        </div>
                        <div className="text-gray-400">
                          {(position.bbox[2] - position.bbox[0]).toFixed(1)} ×{" "}
                          {(position.bbox[3] - position.bbox[1]).toFixed(1)}
                        </div>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => {
                          removePosition(position.id);
                          recordAction({
                            type: "delete",
                            position: position,
                            deleteIndex: index,
                          });
                        }}
                        className="text-xs px-1 py-0 h-6 text-red-600 hover:text-red-800 hover:bg-red-50"
                      >
                        删除
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : (
          <div className="text-sm text-gray-400 text-center mt-8">
            暂无位置数据
            <div className="text-xs mt-1">
              在编辑器中选择文本或在PDF中绘制红框
            </div>
          </div>
        )}
      </div>
      <div className="flex justify-end">
        <Button
          onClick={async () => {
            const result = await saveDomData();
            if (result) {
              toast.success("保存成功");
            } else {
              toast.error("保存失败");
            }
          }}
        >
          保存
        </Button>
      </div>
    </div>
  );
};

interface DomPanelProps {
  name: string | null;
  width: number;
  setHighlightedPath: (path: number[] | null) => void;
}

const DomPanel: React.FC<DomPanelProps> = ({
  name,
  width,
  setHighlightedPath,
}) => {
  const {
    domData,
    documentContent,
    focusBlock,
    clearFocusedBlock,
    initEditor,
    setPdfPage,
    lastSaveTime,
    updateEditingDomData,
    screenShotLoading,
  } = useDocumentParserStore();

  // 创建基础的 BlockNote 编辑器
  const editor = useCreateBlockNote({
    dictionary: zh,
    schema: customSchema,
  });

  useEffect(() => {
    initEditor(editor as unknown as BlockNoteEditor);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 监听documentData变化并更新编辑器内容
  useEffect(() => {
    if (domData) {
      const newBlocks = documentDataToBlocks(domData);

      // 简单的内容比较 - 比较blocks数量和第一个block的内容
      const currentBlocks = editor.document;
      // 使用事务来避免多次触发onChange
      editor.transact(() => {
        // 替换编辑器中的所有内容
        if (newBlocks.length > 0) {
          editor.replaceBlocks(
            currentBlocks.map((block) => block.id),
            newBlocks,
          );
          if (newBlocks.length > 0) {
            editor.setTextCursorPosition(newBlocks[0].id);
          }
        } else {
          // 如果没有新内容，清空编辑器
          editor.removeBlocks(currentBlocks.map((block) => block.id));
        }
      });
    }
  }, [domData, editor]);

  const handleCursorChange = () => {
    const textCursorPosition = editor.getTextCursorPosition();

    if (textCursorPosition && textCursorPosition.block) {
      const currentBlock = textCursorPosition.block as Block;

      // 查找对应的DocumentNode来获取positions和节点信息
      const findPositionsForBlock = (
        block: Block,
      ): {
        positions:
          | {
              bbox: [number, number, number, number];
              page: number;
              id: string;
              type: "auto" | "manual";
            }[]
          | null;
        node: DocumentNode | null;
      } => {
        const findInNode = (
          node: DocumentNode,
          targetBlock: Block,
        ): {
          positions:
            | {
                bbox: [number, number, number, number];
                page: number;
                id: string;
                type: "auto" | "manual";
              }[]
            | null;
          node: DocumentNode | null;
        } => {
          if (node.path.join("-") === targetBlock.id) {
            return {
              positions:
                node.element?.positions?.map((pos) => ({
                  ...pos,
                  id: `auto-${Date.now()}-${pos.page}-${pos.bbox[0]}-${pos.bbox[1]}-${pos.bbox[2]}-${pos.bbox[3]}`,
                  type: "auto" as const,
                })) || null,
              node: node,
            };
          }
          // 递归搜索子节点
          if (node.children) {
            for (const child of node.children) {
              const result = findInNode(child, targetBlock);
              if (result.positions) return result;
            }
          }

          return { positions: null, node: null };
        };

        if (domData && domData.children) {
          for (const child of domData.children) {
            const result = findInNode(child, block);
            if (result.positions) return result;
          }
        }
        return { positions: null, node: null };
      };

      const result = findPositionsForBlock(currentBlock);
      const positions = result.positions;
      const foundNode = result.node;
      focusBlock(currentBlock, positions);

      // 同步PDF页码 - 如果当前块有位置信息，跳转到第一个位置的页码
      if (positions && positions.length > 0) {
        const firstPosition = positions[0];
        setPdfPage(firstPosition.page + 1); // positions 使用 0 基索引，PDF 使用 1 基索引
      }

      // 同步OutlinePanel滚动到对应节点并高亮
      if (foundNode && foundNode.path) {
        // 设置高亮路径
        setHighlightedPath(foundNode.path);

        setTimeout(() => {
          const getOutlineElement = (path: number[]) => {
            let currentPath = path;
            while (currentPath.length > 0) {
              const outlineElement = document.querySelector(
                `[data-path="${currentPath.join("-")}"]`,
              );
              if (outlineElement) {
                return outlineElement;
              }
              currentPath = currentPath.slice(0, -1);
            }
            return null;
          };
          const outlineElement = getOutlineElement(foundNode.path);
          if (outlineElement) {
            outlineElement.scrollIntoView({
              behavior: "smooth",
              block: "center",
            });
          }
          const overlayElement = document.querySelector(`[data-overlay="0"]`);
          if (overlayElement) {
            overlayElement.scrollIntoView({
              behavior: "smooth",
              block: "center",
            });
          }
        }, 100);
      } else {
        // 清除高亮
        setHighlightedPath(null);
      }
    } else {
      clearFocusedBlock();
    }
  };
  const throttleUpdateEditingDomData = useThrottleFn(
    () => {
      const blocks = editor.document as Block[];
      const documentData = blocksToDocumentData(blocks, domData!);
      updateEditingDomData(documentData);
    },
    {
      wait: 500,
      leading: true,
      trailing: true,
    },
  );
  return (
    <div
      className="flex flex-col overflow-hidden relative"
      style={{ width: `${width}%` }}
    >
      <div className="py-2 pl-4 flex items-center border-b">
        <span className="text-base font-semibold flex-shrink-0 leading-8">
          知识文档
        </span>
        <span className="truncate text-ellipsis ml-2 text-sm font-medium text-gray-500">
          {name || "未命名文档"}
        </span>
        {lastSaveTime && (
          <span className="ml-2 mr-4 text-xs font-medium text-gray-500 flex-shrink-0">
            最后保存时间：{lastSaveTime}
          </span>
        )}
      </div>
      <div className="h-full overflow-y-auto pt-4 relative">
        <BlockNoteView
          theme="light"
          editor={editor}
          sideMenu={false}
          slashMenu={false}
          formattingToolbar={false}
          onChange={() => throttleUpdateEditingDomData.run()}
          onSelectionChange={() => handleCursorChange()}
        >
          <SuggestionMenuController
            triggerCharacter="/"
            suggestionMenuComponent={CustomSlashMenu}
            getItems={async (query) =>
              filterSuggestionItems(
                getCustomSlashMenuItems(
                  editor as unknown as BlockNoteEditor,
                  documentContent,
                ),
                query,
              )
            }
          ></SuggestionMenuController>
          <SideMenuController
            sideMenu={(props) => (
              <SideMenu {...props}>
                <SideMenuIcon block={props.block} />
                <DragHandleButton {...props}>
                  <ResetBlockTypeItems
                    {...props}
                    documentContent={documentContent}
                  />
                  <RemoveBlockItem {...props}>
                    <div className="text-red-400 flex items-center gap-2">
                      <Trash className="size-4" />
                      删除
                    </div>
                  </RemoveBlockItem>
                </DragHandleButton>
              </SideMenu>
            )}
          />
        </BlockNoteView>
      </div>
      {screenShotLoading && (
        <div className="absolute inset-0 bg-black/50 z-50 h-full">
          <div className="flex justify-center items-center h-full text-white">
            <Spinner size="md" />
            处理截图中...
          </div>
        </div>
      )}
    </div>
  );
};

const getCustomSlashMenuItems = (
  editor: BlockNoteEditor,
  documentContent: {
    type: "pdf" | "docx";
  } | null,
) => {
  const noNeedItems = [
    "quote",
    "toggle_list",
    "check_list",
    "numbered_list",
    "video",
    "audio",
    "file",
    "toggle_heading",
    "toggle_heading_2",
    "toggle_heading_3",
    "emoji",
    "image",
  ];
  const items = [...getDefaultReactSlashMenuItems(editor)]
    .filter(
      (item) => "key" in item && !noNeedItems.includes(item.key as string),
    )
    .map((item) => ({
      ...item,
      group: "",
      subtext: "",
      badge: "",
      icon:
        item.title === "一级标题" ? (
          <Heading1 size={16} />
        ) : item.title === "二级标题" ? (
          <Heading2 size={16} />
        ) : item.title === "三级标题" ? (
          <Heading3 size={16} />
        ) : item.title === "表格" ? (
          <TableIcon size={16} />
        ) : item.title.includes("列表") ? (
          <List size={16} />
        ) : item.title.includes("代码") ? (
          <Code size={16} />
        ) : item.title === "段落" ? (
          <Text size={16} />
        ) : null,
    }));
  const headingItems: DefaultReactSuggestionItem[] = items
    // @ts-ignore
    .filter((item) => item.key?.startsWith("heading"))
    // @ts-ignore
    .concat([
      {
        title: "四级标题",
        onItemClick: () => {
          insertOrUpdateBlock(editor, {
            type: "heading",
            props: { level: 4 },
          });
        },
        icon: <Heading4 size={16} />,
        aliases: ["h4", "heading4", "四级标题", "sijibiaoti"],
        key: "heading_4",
      },
      {
        title: "五级标题",
        onItemClick: () => {
          insertOrUpdateBlock(editor, {
            type: "heading",
            props: { level: 5 },
          });
        },
        icon: <Heading5 size={16} />,
        aliases: ["h5", "heading5", "五级标题", "wujibiaoti"],
        key: "heading_5",
      },
      {
        title: "六级标题",
        onItemClick: () => {
          insertOrUpdateBlock(editor, {
            type: "heading",
            props: { level: 6 },
          });
        },
        icon: <Heading6 size={16} />,
        aliases: ["h6", "heading6", "六级标题", "liubiaoti"],
        key: "heading_6",
      },
    ])
    .map((item) => ({
      ...item,
      group: "",
      subtext: "",
      badge: "",
    }));
  // 添加自定义块的斜杠菜单项
  const customItems: DefaultReactSuggestionItem[] = [
    {
      title: "截图",
      onItemClick: () => {
        if (documentContent?.type !== "pdf") {
          toast.error("当前仅支持PDF文档的截图功能");
          return;
        }

        // 设置截图模式
        const { setScreenshotMode } = store.getState();
        toast.info("进入截图模式，点击ESC退出");
        setScreenshotMode(true, (imageUrl, bbox, page) => {
          // 截图完成后，在当前位置插入图片块
          const insertedBlock = insertOrUpdateBlock(editor, {
            type: "image",
            props: {
              url: imageUrl,
            },
          });

          // 给插入的图片块添加position信息
          const position = {
            bbox: bbox,
            page: page,
            id: `screenshot-${Date.now()}`,
            type: "manual" as const,
          };

          // 使用store中的attachPositionsToBlock来设置position
          const { attachPositionsToBlock } = store.getState();
          attachPositionsToBlock(insertedBlock, [position]);
        });
      },
      aliases: ["screenshot", "截图", "图片", "jietu", "tupian", "jietu"],
      // @ts-ignore
      key: "screenshot",
      icon: <ImageIcon size={16} />,
    },
    {
      title: "目录",
      onItemClick: () => {
        editor.insertBlocks(
          [
            {
              // @ts-ignore
              type: "catalog",
            },
          ],
          editor.getTextCursorPosition().block,
          "after",
        );
      },
      aliases: ["catalog", "目录", "mulu"],
      // @ts-ignore
      key: "catalog",
      icon: <NotebookText size={16} />,
    },
    {
      title: "数学公式",
      onItemClick: () => {
        insertOrUpdateBlock(editor, {
          // @ts-ignore
          type: "formula",
        });
      },
      aliases: ["formula", "公式", "gongshi", "数学"],
      // @ts-ignore
      key: "formula",
      icon: <Radical size={16} />,
    },
  ];
  const newItems = [
    ...headingItems,
    // @ts-ignore
    ...items.filter((item) => !item.key?.startsWith("heading")),
    ...customItems,
  ];
  return newItems;
};
const CustomSlashMenu = (
  props: SuggestionMenuProps<DefaultReactSuggestionItem>,
) => {
  return (
    <div className="bn-mantine mantine-Menu-dropdown p-2 px-3">
      {props.items.map((item, index) => (
        <div
          className={cn(
            props.selectedIndex === index ? "bg-gray-100" : "",
            "flex items-center gap-2 cursor-pointer hover:bg-gray-100 rounded p-2 text-xs",
          )}
          key={index}
          onClick={() => props.onItemClick?.(item)}
        >
          {item.icon}
          {item.title}
        </div>
      ))}
    </div>
  );
};

interface ParserProps {
  width: number;
}

const Parser: React.FC<ParserProps> = ({ width }) => {
  const { domData, documentContent, editor } = useDocumentParserStore();

  // 从localStorage读取初始宽度，如果不存在则使用默认值60%
  const [domPanelWidth, setDomPanelWidth] = useLocalState("domPanelWidth", 60);

  // 当前高亮的节点路径
  const [highlightedPath, setHighlightedPath] = useState<number[] | null>(null);

  const handleNavigateToNode = (node: DocumentNode) => {
    if (!domData || !editor) {
      return;
    }

    const targetText = node.element?.text?.trim();
    if (!targetText) {
      return;
    }
    const blocks = editor.document;

    // 查找文本相等的Block
    for (const block of blocks) {
      let blockText = "";

      if (Array.isArray(block.content)) {
        blockText = block.content
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          .map((item: any) => {
            if (typeof item === "string") return item;
            if (typeof item === "object" && item.text) return item.text;
            return "";
          })
          .join("")
          .trim();
      }

      if (blockText === targetText) {
        try {
          // 设置光标到目标块
          editor.setTextCursorPosition(block, "start");

          // 滑动到目标块
          setTimeout(() => {
            const blockElement = document.querySelector(
              `[data-id="${block.id}"]`,
            );
            if (blockElement) {
              blockElement.scrollIntoView({
                behavior: "smooth",
                block: "center",
              });
            }
          }, 100);

          break;
        } catch (error) {
          console.error("跳转失败:", error);
        }
      }
    }
  };

  // 拖动处理逻辑
  const handleMouseDown = (e: React.MouseEvent) => {
    e.preventDefault();
    const startX = e.clientX;
    const startWidth = domPanelWidth;

    const handleMouseMove = (e: MouseEvent) => {
      const deltaX = e.clientX - startX;
      const containerWidth = window.innerWidth - 300; // 减去左侧面板宽度
      const deltaPercentage = (deltaX / containerWidth) * 100;
      const newWidth = Math.max(30, Math.min(80, startWidth + deltaPercentage)); // 限制在30%-80%之间
      setDomPanelWidth(newWidth);
    };

    const handleMouseUp = () => {
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
      document.body.style.cursor = "default";
      document.body.style.userSelect = "auto";
    };

    document.addEventListener("mousemove", handleMouseMove);
    document.addEventListener("mouseup", handleMouseUp);
    document.body.style.cursor = "ew-resize";
    document.body.style.userSelect = "none";
  };

  if (!domData) return null;
  return (
    <div
      className="h-full flex flex-col overflow-hidden"
      style={{ width: `${width}%` }}
    >
      <div className="flex flex-1 h-full overflow-hidden">
        <OutlinePanel
          onNavigateToNode={handleNavigateToNode}
          highlightedPath={highlightedPath}
        />
        <DomPanel
          name={documentContent?.name || "未命名文档"}
          width={domPanelWidth}
          setHighlightedPath={setHighlightedPath}
        />
        {/* 拖动条 */}
        <div
          className="w-[1px] bg-gray-200 hover:bg-gray-300 cursor-ew-resize flex-shrink-0 relative group"
          onMouseDown={handleMouseDown}
        >
          <div className="absolute inset-0 -mx-1 group-hover:bg-opacity-20" />
        </div>
        <RightPanel width={100 - domPanelWidth} />
      </div>
    </div>
  );
};

export default Parser;
