"use client";

import React, {
  useState,
  useRef,
  forwardRef,
  useImperativeHandle,
  useEffect,
} from "react";
import { cn } from "@/lib/utils";
import {
  DocumentData,
  DocumentNode,
  DocumentElement,
} from "@/lib/types/documents";
import { ScrollArea, ScrollBar } from "./ui/scroll-area";
import { Button } from "./ui/button";
import {
  ChevronRight,
  ChevronDown,
  List,
  ArrowLeft,
  ArrowRight,
} from "lucide-react";
import { webRequest } from "@/lib/request/web";
import { TableRenderer } from "./table-renderer";
import { Input } from "./ui/input";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { tomorrow } from "react-syntax-highlighter/dist/esm/styles/prism";
import { Copy, Check } from "lucide-react";
import { toast } from "sonner";
import DragWidthBar from "./drag-width-bar";
import { useLocalState } from "@/hooks/use-local-state";
import { HighlightedText } from "./hightlighted-text";

interface CodeRendererProps {
  code: string;
  className?: string;
  showLineNumbers?: boolean;
  showHeader?: boolean;
}

const CodeRenderer: React.FC<CodeRendererProps> = ({
  code,
  className,
  showLineNumbers = true,
  showHeader = true,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      // HTTPS 协议下，navigator.clipboard 才存在
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(code);
        setCopied(true);
        toast.success("复制成功", {
          onAutoClose: () => {
            setCopied(false);
          },
        });
        return;
      }

      // 降级方案：使用传统的 execCommand 方法
      const textArea = document.createElement("textarea");
      textArea.value = code;
      textArea.style.position = "fixed";
      textArea.style.left = "-999999px";
      textArea.style.top = "-999999px";
      document.body.appendChild(textArea);
      textArea.focus();
      textArea.select();

      const successful = document.execCommand("copy");
      document.body.removeChild(textArea);

      if (successful) {
        setCopied(true);
        toast.success("复制成功", {
          onAutoClose: () => {
            setCopied(false);
          },
        });
      } else {
        throw new Error("execCommand failed");
      }
    } catch {
      toast.error("复制失败，请手动选择复制");
    }
  };

  return (
    <div className={cn("relative group", className)}>
      {showHeader && (
        <div className="flex items-center justify-between bg-gray-800 text-gray-200 px-4 py-2 text-sm rounded-t-md">
          <div className="flex items-center gap-2">
            <span className="text-gray-400">代码</span>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={handleCopy}
            className="h-6 w-6 p-0 text-gray-400 hover:text-white hover:bg-gray-700"
          >
            {copied ? (
              <Check className="h-3 w-3" />
            ) : (
              <Copy className="h-3 w-3" />
            )}
          </Button>
        </div>
      )}
      <div className="relative">
        <SyntaxHighlighter
          language="text"
          style={tomorrow}
          showLineNumbers={showLineNumbers}
          customStyle={{
            margin: 0,
            borderTopLeftRadius: showHeader ? 0 : "6px",
            borderTopRightRadius: showHeader ? 0 : "6px",
            borderBottomLeftRadius: "6px",
            borderBottomRightRadius: "6px",
            fontSize: "14px",
            lineHeight: "1.5",
          }}
          codeTagProps={{
            style: {
              fontSize: "14px",
              fontFamily:
                'Monaco, "Cascadia Code", "Segoe UI Mono", "Roboto Mono", Consolas, "Courier New", monospace',
            },
          }}
        >
          {code}
        </SyntaxHighlighter>
        {!showHeader && (
          <Button
            variant="ghost"
            size="sm"
            onClick={handleCopy}
            className="absolute top-2 right-2 h-6 w-6 p-0 opacity-0 group-hover:opacity-100 transition-opacity bg-gray-800/80 text-gray-200 hover:bg-gray-700 hover:text-white"
          >
            {copied ? (
              <Check className="h-3 w-3" />
            ) : (
              <Copy className="h-3 w-3" />
            )}
          </Button>
        )}
      </div>
    </div>
  );
};

interface NodeComponentProps {
  node: DocumentNode;
  level: number;
  highlightedNode: DocumentNode | null;
  keyword: string;
  onNodeClick: (node: DocumentNode) => void;
  onNodeDoubleClick: (node: DocumentNode) => void;
  nodeRefs: React.RefObject<Map<string, HTMLDivElement>>;
}

const NodeComponent: React.FC<NodeComponentProps> = ({
  node,
  level,
  highlightedNode,
  keyword,
  onNodeClick,
  onNodeDoubleClick,
  nodeRefs,
}) => {
  const nodeKey = node.path ? node.path.join("-") : "";
  const isHighlighted =
    highlightedNode &&
    node.path &&
    highlightedNode.path &&
    highlightedNode.path.length === node.path.length &&
    highlightedNode.path.every((v, i) => v === node.path[i]);
  const nodeRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (nodeRef.current && node.path) {
      nodeRefs.current.set(nodeKey, nodeRef.current);
    }
    return () => {
      if (node.path) {
        // eslint-disable-next-line react-hooks/exhaustive-deps
        nodeRefs.current.delete(nodeKey);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodeKey, nodeRefs]);

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    onNodeClick(node);
  };

  const handleDoubleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    onNodeDoubleClick(node);
  };

  const renderElement = (element: DocumentElement) => {
    const baseClasses = cn(
      "transition-all duration-50 cursor-pointer rounded-md p-2 hover:bg-gray-50",
      isHighlighted && "bg-blue-100 border-2 border-blue-300 shadow-md",
    );

    switch (element.type) {
      case "Title":
        return (
          <HighlightedText
            className={cn(baseClasses, "font-bold text-lg mb-2", {
              "text-2xl": level === 0,
              "text-xl": level === 1,
              "text-lg": level === 2,
              "text-base": level > 2,
            })}
            text={element.text || ""}
            keyword={keyword}
          ></HighlightedText>
        );

      case "List":
        return (
          <div
            className={cn(baseClasses, "mb-2 pl-4 border-l-2 border-gray-200")}
          >
            <HighlightedText
              className="text-gray-700"
              text={element.text || ""}
              keyword={keyword}
            ></HighlightedText>
          </div>
        );

      case "Table":
        return (
          <div className={cn(baseClasses, "mb-4")}>
            <div className="overflow-x-auto">
              <TableRenderer rows={element.rows || []} keyword={keyword} />
            </div>
          </div>
        );
      case "Figure":
        return (
          <figure className={cn(baseClasses)}>
            {element.image?.type === "image_base64" && (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                className="w-full"
                src={element.image.base64}
                alt={element.name}
              ></img>
            )}
            {element.image?.type === "image_url" && (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                className="w-full"
                src={element.image.url}
                alt={element.name}
              />
            )}
          </figure>
        );
      case "Code":
        return (
          <div className={cn(baseClasses, "mb-4")}>
            <CodeRenderer
              code={element.text || ""}
              showLineNumbers={true}
              showHeader={true}
              className="rounded-md overflow-hidden"
            />
          </div>
        );
      case "Formula":
        return (
          <HighlightedText
            className={cn(baseClasses, "mb-2 leading-relaxed")}
            text={element.text || ""}
            keyword={keyword}
          ></HighlightedText>
        );
      case "Text":

      default:
        return (
          <div className={cn(baseClasses, "mb-2 leading-relaxed")}>
            <HighlightedText
              className="text-gray-800 whitespace-pre-wrap"
              text={element.text || ""}
              keyword={keyword}
            ></HighlightedText>
          </div>
        );
    }
  };
  if (node.element.type === "Title" && !node.element.text) {
    return (
      node.children &&
      node.children.length > 0 &&
      node.children.map((childNode, index) => (
        <NodeComponent
          key={childNode.path ? childNode.path.join("-") : index}
          node={childNode}
          level={level + 1}
          highlightedNode={highlightedNode}
          onNodeClick={onNodeClick}
          onNodeDoubleClick={onNodeDoubleClick}
          nodeRefs={nodeRefs}
          keyword={keyword}
        />
      ))
    );
  }
  return (
    <div className={cn("mb-1")}>
      <div
        className="cursor-pointer"
        ref={nodeRef}
        onClick={handleClick}
        onDoubleClick={handleDoubleClick}
      >
        {renderElement(node.element)}
      </div>
      {node.children &&
        node.children.length > 0 &&
        node.children.map((childNode, index) => (
          <NodeComponent
            key={childNode.path ? childNode.path.join("-") : index}
            node={childNode}
            level={level + 1}
            highlightedNode={highlightedNode}
            keyword={keyword}
            onNodeClick={onNodeClick}
            onNodeDoubleClick={onNodeDoubleClick}
            nodeRefs={nodeRefs}
          />
        ))}
    </div>
  );
};

// 大纲树节点组件
interface OutlineNodeProps {
  node: DocumentNode;
  level: number;
  onNodeClick: (node: DocumentNode) => void;
  onNodeDoubleClick: (node: DocumentNode) => void;
  highlightedNode: DocumentNode | null;
}

const OutlineNode: React.FC<OutlineNodeProps> = ({
  node,
  level,
  onNodeClick,
  onNodeDoubleClick,
  highlightedNode,
}) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const hasChildren = node.children && node.children.length > 0;
  const nodeKey = node.path ? node.path.join("-") : "";
  const isHighlighted =
    (highlightedNode?.element.type === "Title" && node === highlightedNode) ||
    (highlightedNode?.element.type !== "Title" &&
      node.path.length === (highlightedNode?.path.length || 0) - 1 &&
      node.path.every((v, i) => v === highlightedNode?.path[i]));
  const shouldShowInOutline =
    node.element.type === "Title" && node.element.text;
  if (!shouldShowInOutline && !hasChildren) {
    return null;
  }
  return (
    <div className="outline-node" data-id={nodeKey}>
      {shouldShowInOutline && (
        <div
          className={cn(
            "flex items-center gap-1 py-1 px-2 rounded cursor-pointer hover:bg-gray-100 transition-colors",
            isHighlighted && "bg-blue-100 text-blue-800",
          )}
          style={{
            paddingLeft: `${level * 12 + 8}px`,
          }}
          onClick={() => onNodeClick(node)}
          onDoubleClick={() => onNodeDoubleClick(node)}
        >
          {hasChildren && (
            <Button
              variant="ghost"
              size="sm"
              className="h-4 w-4 relative"
              onClick={(e) => {
                e.stopPropagation();
                setIsExpanded(!isExpanded);
              }}
            >
              {isExpanded ? (
                <ChevronDown className="h-3 w-3" />
              ) : (
                <ChevronRight className="h-3 w-3" />
              )}
            </Button>
          )}
          <span
            className="text-sm truncate cursor-pointer"
            title={node.element.text}
          >
            {node.element.text || `节点 ${nodeKey}`}
          </span>
        </div>
      )}
      {hasChildren && isExpanded && (
        <div>
          {node.children!.map((childNode, index) => (
            <OutlineNode
              key={childNode.path ? childNode.path.join("-") : index}
              node={childNode}
              level={shouldShowInOutline ? level + 1 : level}
              onNodeClick={onNodeClick}
              onNodeDoubleClick={onNodeDoubleClick}
              highlightedNode={highlightedNode}
            />
          ))}
        </div>
      )}
    </div>
  );
};

// 大纲面板组件
interface OutlinePanelProps {
  data: DocumentData;
  onInternalNodeClick: (node: DocumentNode) => void;
  onInternalNodeDoubleClick: (node: DocumentNode) => void;
  highlightedNode: DocumentNode | null;
  outlinePanelWidth: number;
}

const OutlinePanel: React.FC<OutlinePanelProps> = ({
  data,
  onInternalNodeClick,
  onInternalNodeDoubleClick,
  highlightedNode,
  outlinePanelWidth,
}) => {
  return (
    <div
      className="flex flex-col border-r border-gray-200 bg-gray-50 p-2"
      style={{ width: outlinePanelWidth + "%" }}
    >
      <ScrollArea className="flex flex-1 overflow-hidden">
        <div className="space-y-1">
          {data.children &&
            data.children.map((node, index) => (
              <OutlineNode
                key={node.path ? node.path.join("-") : index}
                node={node}
                level={0}
                onNodeClick={onInternalNodeClick}
                onNodeDoubleClick={onInternalNodeDoubleClick}
                highlightedNode={highlightedNode}
              />
            ))}
        </div>
        <ScrollBar orientation="horizontal" />
        <ScrollBar orientation="vertical" />
      </ScrollArea>
    </div>
  );
};

const KeywordSearch: React.FC<{
  data: DocumentData;
  keyword: string;
  onKeywordChange: (keyword: string) => void;
  onScrollToNode: (node: DocumentNode) => void;
  onClearHighlightedNode: () => void;
}> = ({
  data,
  onScrollToNode,
  onClearHighlightedNode,
  keyword,
  onKeywordChange,
}) => {
  const searchNodesRef = useRef<DocumentNode[]>([]);
  const [searchNodesLength, setSearchNodesLength] = useState(0);
  const [currentSearchIndex, setCurrentSearchIndex] = useState(0);
  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newKeyword = e.target.value;
    onKeywordChange(newKeyword);
    if (newKeyword.length === 0) {
      searchNodesRef.current = [];
      setSearchNodesLength(0);
      setCurrentSearchIndex(0);
      onClearHighlightedNode();
      return;
    }
    const searchNodes: DocumentNode[] = [];
    // 搜索节点
    if (newKeyword.startsWith("/")) {
      const findNodeByPath = (nodes: DocumentNode[], path: number[]) => {
        for (const node of nodes) {
          if (node.path && path.every((v, i) => v === node.path[i])) {
            searchNodes.push(node);
          }
          if (node.children && node.children.length > 0) {
            findNodeByPath(node.children, path);
          }
        }
      };
      const path = newKeyword
        .split("/")
        .slice(1)
        .map((v) => Number(v));
      const isValidPath = path.every((v) => !isNaN(Number(v)));
      if (!isValidPath) {
        toast.error("路径搜索格式错误，请使用数字路径");
        return;
      }
      findNodeByPath(data.children || [], path);
    } else {
      const findNodesByKeyword = (nodes: DocumentNode[]) => {
        for (const node of nodes) {
          if (node.element.text && node.element.text.includes(newKeyword)) {
            searchNodes.push(node);
          }
          if (node.element.type === "Table") {
            const rows = node.element.rows || [];
            const found = rows.find((row) =>
              row.cells.find((cell) => cell.text?.includes(newKeyword)),
            );
            if (found) {
              searchNodes.push(node);
            }
          }
          if (node.children && node.children.length > 0) {
            findNodesByKeyword(node.children);
          }
        }
      };
      findNodesByKeyword(data.children || []);
    }
    // 获取到了搜索的节点，更新状态
    searchNodesRef.current = searchNodes;
    setSearchNodesLength(searchNodes.length);
    // 如果搜索到节点，则跳转到第一个节点
    if (searchNodes.length > 0) {
      setCurrentSearchIndex(0);
      onScrollToNode(searchNodesRef.current[0]);
    } else {
      // 如果搜索不到，需要将节点高亮等状态移除
      setCurrentSearchIndex(0);
      onClearHighlightedNode();
    }
  };
  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.nativeEvent.isComposing) {
      return;
    }
    if (e.key === "Enter") {
      if (keyword.length === 0) {
        return;
      }
      // 如果关键词相同，则跳转到下一个
      if (searchNodesRef.current.length > 0) {
        if (e.shiftKey) {
          const preIndex =
            currentSearchIndex - 1 >= 0
              ? currentSearchIndex - 1
              : searchNodesLength - 1;
          setCurrentSearchIndex(preIndex);
          onScrollToNode(searchNodesRef.current[preIndex]);
          return;
        }
        const nextIndex =
          currentSearchIndex + 1 >= searchNodesRef.current.length
            ? 0
            : currentSearchIndex + 1;
        setCurrentSearchIndex(nextIndex);
        onScrollToNode(searchNodesRef.current[nextIndex]);
        return;
      }
    }
  };

  return (
    <div className="flex items-center gap-2">
      <div className="relative flex items-center gap-2">
        <Input
          className="w-78 pr-8"
          placeholder="请输入关键词或节点路径，按下回车后搜索"
          value={keyword}
          onChange={onChange}
          onKeyDown={onKeyDown}
        />
        <div className="absolute right-2 text-sm text-gray-500">
          {searchNodesLength > 0
            ? `${currentSearchIndex + 1}/${searchNodesLength}`
            : keyword.length > 0
              ? "0/0"
              : ""}
        </div>
      </div>

      <Button
        variant="ghost"
        size="sm"
        onClick={() => {
          if (searchNodesLength === 0) {
            return;
          }
          const preIndex =
            currentSearchIndex - 1 >= 0
              ? currentSearchIndex - 1
              : searchNodesLength - 1;
          setCurrentSearchIndex(preIndex);
          onScrollToNode(searchNodesRef.current[preIndex]);
        }}
      >
        <ArrowLeft className="h-4 w-4" />
      </Button>
      <Button
        variant="ghost"
        size="sm"
        onClick={() => {
          if (searchNodesLength === 0) {
            return;
          }
          const nextIndex =
            currentSearchIndex + 1 >= searchNodesLength
              ? 0
              : currentSearchIndex + 1;
          setCurrentSearchIndex(nextIndex);
          onScrollToNode(searchNodesRef.current[nextIndex]);
        }}
      >
        <ArrowRight className="h-4 w-4" />
      </Button>
    </div>
  );
};

export interface DocumentViewerRef {
  scrollToNode: (path: number[]) => void;
  highlightNode: (path: number[]) => void;
  scrollToAndHighlightNode: (path: number[]) => void;
}

interface DocumentViewerProps {
  fileId: string;
  onClickNode: (node: DocumentNode) => void;
  onDoubleClickNode?: (node: DocumentNode) => void;
  showOutline?: boolean;
}

const DocumentViewer = forwardRef<DocumentViewerRef, DocumentViewerProps>(
  function DocumentViewer(
    { fileId, onClickNode, onDoubleClickNode, showOutline = true },
    ref,
  ) {
    const [highlightedNode, setHighlightedNode] = useState<DocumentNode | null>(
      null,
    );
    const [showOutlinePanel, setShowOutlinePanel] = useState(showOutline);
    const [outlinePanelWidth, setOutlinePanelWidth] = useLocalState(
      "outlinePanelWidth",
      30,
    );
    const [data, setData] = useState<DocumentData | null>(null);
    const [message, setMessage] = useState("");
    const fileDomData = useRef<{ fileId: string; data: DocumentData }[]>([]);
    const altKeyRef = useRef(false);
    const containerRef = useRef<HTMLDivElement | null>(null);
    const [searchKeyword, setSearchKeyword] = useState("");
    useEffect(() => {
      const handleKeyDown = (e: KeyboardEvent) => {
        if (e.altKey) {
          altKeyRef.current = true;
        } else {
          altKeyRef.current = false;
        }
      };
      document.addEventListener("keydown", handleKeyDown);
      return () => {
        document.removeEventListener("keydown", handleKeyDown);
      };
    }, []);
    useEffect(() => {
      if (fileDomData.current.find((item) => item.fileId === fileId)) {
        setData(
          fileDomData.current.find((item) => item.fileId === fileId)!.data,
        );
      } else if (fileId) {
        webRequest<{ url: string }>({
          path: "/api/dom-tree",
          method: "GET",
          query: { fileId },
        }).then((res) => {
          if (res.data.url) {
            setMessage("正在获取文档内容，请稍后...");
            const url = res.data.url;
            fetch(url).then((res) => {
              res.json().then((data) => {
                setData(data);
                fileDomData.current.push({ fileId, data });
              });
            });
          } else {
            setMessage("当前文档不支持解析");
            setData(null);
          }
        });
      }
    }, [fileId]);
    const scrollAreaRef = useRef<HTMLDivElement>(null);
    const nodeRefs = useRef<Map<string, HTMLDivElement>>(new Map());
    const findNodeByPath = (
      nodes: DocumentNode[],
      path: number[],
    ): DocumentNode | null => {
      for (const node of nodes) {
        if (
          node.path &&
          node.path.length === path.length &&
          node.path.every((v, i) => v === path[i])
        ) {
          return node;
        }
        if (node.children && node.children.length > 0) {
          const found = findNodeByPath(node.children, path);
          if (found) return found;
        }
      }
      return null;
    };

    const handleNodeClick = (node: DocumentNode) => {
      setHighlightedNode(node);
      if (onClickNode) {
        onClickNode(node);
      }
      if (altKeyRef.current) {
        onDoubleClickNode?.(node);
      }
      // 滚动到左侧目录的对应节点
      if (node.path && showOutlinePanel) {
        const path = node.path.slice();
        while (path.length > 0) {
          const element = document.querySelector(
            `.outline-node[data-id="${path.join("-")}"]`,
          );
          if (element) {
            element.scrollIntoView({
              behavior: "smooth",
              block: "start",
            });
            break;
          }
          path.pop();
        }
      }
    };

    const handleNodeDoubleClick = (node: DocumentNode) => {
      setHighlightedNode(node);
      if (onDoubleClickNode) {
        onDoubleClickNode(node);
      }
    };

    const handleOutlineNodeClick = (node: DocumentNode) => {
      setHighlightedNode(node);
      if (node.path) {
        setTimeout(() => {
          const element = nodeRefs.current.get(node.path.join("-"));
          if (element) {
            element.scrollIntoView({
              behavior: "smooth",
              block: "center",
            });
          }
        }, 100);
      }
    };

    const handleOutlineNodeDoubleClick = (node: DocumentNode) => {
      setHighlightedNode(node);
      if (onDoubleClickNode) {
        onDoubleClickNode(node);
      }
    };

    const handleScrollToNode = (node: DocumentNode) => {
      setHighlightedNode(node);
      const element = nodeRefs.current.get(node.path?.join("-") || "");
      if (element) {
        element.scrollIntoView({
          behavior: "smooth",
          block: "center",
        });
      }
    };

    useImperativeHandle(ref, () => ({
      scrollToNode: (path: number[]) => {
        const element = nodeRefs.current.get(path.join("-"));
        if (element) {
          element.scrollIntoView({
            behavior: "smooth",
            block: "center",
          });
        }
      },
      highlightNode: (path: number[]) => {
        if (data) {
          const node = findNodeByPath(data.children || [], path);
          if (node) {
            setHighlightedNode(node);
          }
        }
      },
      scrollToAndHighlightNode: (path: number[]) => {
        if (data) {
          const node = findNodeByPath(data.children || [], path);
          if (node) {
            setHighlightedNode(node);
            setTimeout(() => {
              const element = nodeRefs.current.get(path.join("-"));
              if (element) {
                element.scrollIntoView({
                  behavior: "smooth",
                  block: "center",
                });
              }
            }, 100);
          }
        }
      },
    }));
    if (!fileId) {
      return (
        <div className="flex items-center justify-center h-64 text-gray-500">
          请选择文档
        </div>
      );
    }
    if (!data) {
      return (
        <div className="flex items-center justify-center h-64 text-gray-500">
          {message}
        </div>
      );
    }
    return (
      <div
        className="flex h-full bg-gray-50 overflow-hidden"
        ref={containerRef}
      >
        {/* 大纲面板 */}
        {showOutlinePanel && (
          <OutlinePanel
            data={data}
            onInternalNodeClick={handleOutlineNodeClick}
            onInternalNodeDoubleClick={handleOutlineNodeDoubleClick}
            highlightedNode={highlightedNode}
            outlinePanelWidth={outlinePanelWidth}
          />
        )}
        {showOutlinePanel && (
          <DragWidthBar
            containerRef={containerRef}
            minWidthPercentage={20}
            maxWidthPercentage={80}
            localStorageKey="outlinePanelWidth"
            width={showOutlinePanel ? outlinePanelWidth : 0}
            setWidth={setOutlinePanelWidth}
          />
        )}
        {/* 主内容区域 */}
        <div
          className="flex flex-col overflow-hidden"
          style={{
            width: `${100 - (showOutlinePanel ? outlinePanelWidth : 0)}%`,
          }}
        >
          {/* 工具栏 */}
          <div className="flex items-center justify-between p-2 border-b border-gray-200 bg-white">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowOutlinePanel(!showOutlinePanel)}
            >
              <List className="h-4 w-4 mr-1" />
              {showOutlinePanel ? "隐藏大纲" : "显示大纲"}
            </Button>
            <KeywordSearch
              data={data}
              keyword={searchKeyword}
              onKeywordChange={setSearchKeyword}
              onScrollToNode={handleScrollToNode}
              onClearHighlightedNode={() => setHighlightedNode(null)}
            />
          </div>
          {/* 文档内容区域 */}
          <ScrollArea
            ref={scrollAreaRef}
            className="flex-1 overflow-y-auto p-6 space-y-2 scrollbar-hide"
          >
            <div className="bg-white rounded-lg shadow-sm p-6">
              {data.children &&
                data.children.map((node) => (
                  <NodeComponent
                    key={node.path ? node.path.join("-") : undefined}
                    node={node}
                    level={0}
                    keyword={searchKeyword}
                    highlightedNode={highlightedNode}
                    onNodeClick={handleNodeClick}
                    onNodeDoubleClick={handleNodeDoubleClick}
                    nodeRefs={nodeRefs}
                  />
                ))}
            </div>
          </ScrollArea>
        </div>
      </div>
    );
  },
);

export default DocumentViewer;
