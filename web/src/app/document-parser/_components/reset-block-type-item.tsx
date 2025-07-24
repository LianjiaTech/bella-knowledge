import { Block, BlockNoteEditor } from "@blocknote/core";
import {
  DragHandleMenuProps,
  useBlockNoteEditor,
  useComponentsContext,
} from "@blocknote/react";
import { store } from "../model";
import { toast } from "sonner";
import {
  Heading1,
  Heading2,
  Heading3,
  Heading4,
  Heading5,
  Heading6,
  List,
  Text,
  Code,
  Table,
  Image as ImageIcon,
  NotebookText,
  Radical,
} from "lucide-react";

const ResetHeadingItems = (props: DragHandleMenuProps) => {
  const level = 6;
  const titles = [
    "一级标题",
    "二级标题",
    "三级标题",
    "四级标题",
    "五级标题",
    "六级标题",
  ];
  const icons = [
    <Heading1 key={0} className="size-4" />,
    <Heading2 key={1} className="size-4" />,
    <Heading3 key={2} className="size-4" />,
    <Heading4 key={3} className="size-4" />,
    <Heading5 key={4} className="size-4" />,
    <Heading6 key={5} className="size-4" />,
  ];
  return Array.from({ length: level }).map((_, index) => (
    <ResetBlockTypeItem
      key={index}
      {...props}
      updateType="heading"
      onClick={(editor) => {
        editor.updateBlock(props.block, {
          type: "heading",
          props: {
            level: (index + 1) as 1 | 2 | 3 | 4 | 5 | 6,
          },
        });
      }}
    >
      <div className="flex items-center gap-2">
        {icons[index]}
        {titles[index]}
      </div>
    </ResetBlockTypeItem>
  ));
};

const ResetParagraphItem = (props: DragHandleMenuProps) => {
  return (
    <ResetBlockTypeItem {...props} updateType="paragraph">
      <div className="flex items-center gap-2">
        <Text className="size-4" />
        段落
      </div>
    </ResetBlockTypeItem>
  );
};
const ResetBulletedListItemItem = (props: DragHandleMenuProps) => {
  return (
    <ResetBlockTypeItem {...props} updateType="bulletListItem">
      <div className="flex items-center gap-2">
        <List className="size-4" />
        无序列表
      </div>
    </ResetBlockTypeItem>
  );
};

const ResetCodeBlockItem = (props: DragHandleMenuProps) => {
  return (
    <ResetBlockTypeItem {...props} updateType="codeBlock">
      <div className="flex items-center gap-2">
        <Code className="size-4" />
        代码块
      </div>
    </ResetBlockTypeItem>
  );
};

const ResetTableBlockItem = (props: DragHandleMenuProps) => {
  return (
    <ResetBlockTypeItem
      {...props}
      updateType="table"
      onClick={(editor) => {
        // 获取当前块的文本内容
        const text = Array.isArray(props.block.content)
          ? props.block.content
              .map((item) => (item.type === "text" ? item.text : ""))
              .join("")
          : props.block.content;

        // 更新块为表格类型，并设置默认的表格属性
        editor.updateBlock(props.block, {
          type: "table",
          props: {
            ...props.block.props,
          },
          content: {
            type: "tableContent",
            columnWidths: [200], // 默认列宽
            rows: [
              {
                cells: [
                  {
                    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
                    // @ts-ignore
                    type: "tableCell",
                    content: [{ type: "text", text: text || "", styles: {} }],
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
                    content: [],
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
          },
        });
      }}
    >
      <div className="flex items-center gap-2">
        <Table className="size-4" />
        表格
      </div>
    </ResetBlockTypeItem>
  );
};

const ResetImageBlockItem = (
  props: DragHandleMenuProps & {
    documentContent: {
      type: "pdf" | "docx";
    } | null;
  },
) => {
  return (
    <ResetBlockTypeItem
      {...props}
      updateType="image"
      onClick={(editor) => {
        if (props.documentContent?.type !== "pdf") {
          toast.error("当前仅支持PDF文档的截图功能");
          return;
        }
        // 获取当前光标位置和块
        const block = props.block;

        // 设置截图模式
        const { setScreenshotMode } = store.getState();
        toast.info("进入截图模式，点击ESC退出");
        setScreenshotMode(true, (imageUrl, bbox, page) => {
          // 截图完成后，在当前位置插入图片块
          editor.updateBlock(block, {
            type: "image",
            props: {
              url: imageUrl,
            },
          });

          // 给插入的图片块添加position信息
          if (block) {
            const position = {
              bbox: bbox,
              page: page,
              id: `screenshot-${Date.now()}`,
              type: "manual" as const,
            };

            // 使用store中的attachPositionsToBlock来设置position
            const { attachPositionsToBlock } = store.getState();
            attachPositionsToBlock(block, [position]);
          }
          editor.setTextCursorPosition(block, "start");
        });
      }}
    >
      <div className="flex items-center gap-2">
        <ImageIcon className="size-4" />
        截图
      </div>
    </ResetBlockTypeItem>
  );
};

const ResetCatalogBlockItem = (props: DragHandleMenuProps) => {
  return (
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    <ResetBlockTypeItem {...props} updateType="catalog">
      <div className="flex items-center gap-2">
        <NotebookText className="size-4" />
        目录
      </div>
    </ResetBlockTypeItem>
  );
};

const ResetFormulaBlockItem = (props: DragHandleMenuProps) => {
  return (
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    <ResetBlockTypeItem {...props} updateType="formula">
      <div className="flex items-center gap-2">
        <Radical className="size-4" />
        数学公式
      </div>
    </ResetBlockTypeItem>
  );
};

function ResetBlockTypeItem(
  props: DragHandleMenuProps & {
    updateType: Block["type"];
    children: React.ReactNode;
    onClick?: (editor: BlockNoteEditor) => void;
  },
) {
  const editor = useBlockNoteEditor();

  const Components = useComponentsContext()!;

  return (
    <Components.Generic.Menu.Item
      onClick={() => {
        if (props.onClick) {
          props.onClick(editor);
        } else {
          editor.updateBlock(props.block, { type: props.updateType });
        }
      }}
    >
      {props.children}
    </Components.Generic.Menu.Item>
  );
}

export default function ResetBlockTypeItems(
  props: DragHandleMenuProps & {
    documentContent: {
      type: "pdf" | "docx";
    } | null;
  },
) {
  return (
    <>
      <ResetHeadingItems {...props} />
      <ResetBulletedListItemItem {...props} />
      <ResetParagraphItem {...props} />
      <ResetCodeBlockItem {...props} />
      <ResetTableBlockItem {...props} />
      <ResetImageBlockItem {...props} />
      <ResetCatalogBlockItem {...props} />
      <ResetFormulaBlockItem {...props} />
    </>
  );
}
