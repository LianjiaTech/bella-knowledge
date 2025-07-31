"use client";

import { Button } from "@/components/ui/button";
import { Trash } from "lucide-react";
import { DocumentViewerRef } from "@/components/document-viewer";
import { useDocumentPreviewStore } from "../model";
import { useSearchParams } from "next/navigation";
import { forwardRef, useImperativeHandle, useState } from "react";
import { cn } from "@/lib/utils";

export interface ReferenceSectionRef {
  scrollToAndHighlightNode: (path: number[]) => void;
}

interface ReferenceSectionProps {
  documentViewerRef: React.RefObject<DocumentViewerRef | null>;
}

const ReferenceSection = forwardRef<ReferenceSectionRef, ReferenceSectionProps>(
  function ReferenceSection({ documentViewerRef }, ref) {
    const [highlightedPath, setHighlightedPath] = useState<number[]>([]);
    const searchParams = useSearchParams();
    const {
      selectedQuestion,
      qaReferenceList,
      selectFileId,
      referenceFileList,
      deleteQuestionReference,
      onFileSelect,
    } = useDocumentPreviewStore();
    useImperativeHandle(ref, () => ({
      scrollToAndHighlightNode: (path: number[]) => {
        const node = document.querySelector(`[data-path="${path.join("/")}"]`);
        if (node) {
          node.scrollIntoView({ behavior: "smooth", block: "nearest" });
          setHighlightedPath(path);
        } else {
          setHighlightedPath([]);
        }
      },
    }));
    if (!selectedQuestion) {
      return null;
    }

    const currentReferences =
      qaReferenceList.find(
        (reference) => reference.item_id === selectedQuestion?.item_id,
      )?.references || [];

    return (
      <div>
        <div className="text-base font-bold mb-4">Reference(引用)</div>
        <div className="flex flex-col gap-2">
          {currentReferences
            .toSorted((a, b) => {
              const minPathLen = Math.min(a.path.length, b.path.length);
              if (a.path.length === b.path.length) {
                for (let i = 0; i < minPathLen; i++) {
                  if (a.path[i] !== b.path[i]) {
                    return a.path[i] - b.path[i];
                  }
                }
              }
              return a.path.length - b.path.length;
            })
            .map((reference) => (
              <div
                key={reference.file_id + reference.path.join("-")}
                data-path={reference.path.join("/")}
                className={cn(
                  "flex justify-between items-center border border-gray-200 rounded-md p-2 bg-white cursor-pointer hover:bg-gray-50 transition-colors",
                  highlightedPath.join("/") === reference.path.join("/")
                    ? "bg-blue-100"
                    : "",
                )}
                onClick={async () => {
                  setHighlightedPath(reference.path);
                  if (reference.file_id === selectFileId) {
                    // 触发DocumentViewer组件高亮和滑动
                    documentViewerRef.current?.scrollToAndHighlightNode(
                      reference.path.map((item) => Number(item)),
                    );
                    return;
                  } else {
                    onFileSelect(reference.file_id);
                    documentViewerRef.current?.scrollToAndHighlightNode(
                      reference.path,
                    );
                  }
                }}
              >
                <div className="flex flex-col gap-2">
                  <div className="flex items-center flex-1 gap-2">
                    <div className="text-sm font-medium">
                      {
                        referenceFileList.find(
                          (file) => file.id === reference.file_id,
                        )?.filename
                      }
                    </div>
                    <div className="text-xs text-gray-500">
                      节点: /{reference.path.join("/")}
                    </div>
                  </div>
                  {reference.snippet && (
                    <div className="text-xs text-gray-500">
                      {reference.snippet +
                        (reference.snippet.length === 30 ? "..." : "")}
                    </div>
                  )}
                </div>

                <Button
                  variant="outline"
                  size="icon"
                  onClick={(e) => {
                    e.stopPropagation(); // 阻止事件冒泡
                    deleteQuestionReference({
                      dataset_id: searchParams.get("dataset_id") || "",
                      reference_id: reference.reference_id,
                    });
                  }}
                >
                  <Trash />
                </Button>
              </div>
            ))}
          {currentReferences.length === 0 && (
            <div className="text-center text-gray-500 py-8">
              暂无引用，请在右侧文档中点击相关内容添加引用
            </div>
          )}
        </div>
      </div>
    );
  },
);
export { ReferenceSection };
