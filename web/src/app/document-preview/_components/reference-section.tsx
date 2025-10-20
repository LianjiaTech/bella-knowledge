"use client";

import { Button } from "@/components/ui/button";
import { Trash, Star } from "lucide-react";
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
      updateReferencePrimary,
      onFileSelect,
    } = useDocumentPreviewStore();
    const currentReferences =
      qaReferenceList.find(
        (reference) => reference.item_id === selectedQuestion?.item_id,
      )?.references || [];

    useImperativeHandle(ref, () => ({
      scrollToAndHighlightNode: (path: number[]) => {
        const node = currentReferences.find((reference) =>
          reference.path.every((v, i) => Number(v) === Number(path[i])),
        );
        if (node) {
          setHighlightedPath(node.path);
        } else {
          setHighlightedPath([]);
        }
      },
    }));
    if (!selectedQuestion) {
      return null;
    }

    return (
      <div>
        <div className="text-base font-bold mb-4">Reference(引用)</div>
        <div className="flex flex-col gap-2">
          {currentReferences
            .toSorted((a, b) => {
              if (a.file_id !== b.file_id) {
                return a.file_id.localeCompare(b.file_id);
              }
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
                  "flex justify-between items-center border border-gray-200 rounded-md p-2 bg-white cursor-pointer hover:bg-gray-50 transition-colors gap-4",
                  highlightedPath.length >= reference.path.length &&
                    reference.path.every(
                      (v, i) => Number(v) === Number(highlightedPath[i]),
                    )
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
                    <div
                      className="text-sm font-medium flex-1"
                      title={
                        referenceFileList.find(
                          (file) => file.id === reference.file_id,
                        )?.filename
                      }
                    >
                      {
                        referenceFileList.find(
                          (file) => file.id === reference.file_id,
                        )?.filename
                      }
                    </div>
                    <div className="text-xs text-gray-500">
                      节点: {reference.path.length > 0 ? `/${reference.path.join("/")}` : ""}
                    </div>
                  </div>
                  {reference.snippet && (
                    <div className="text-xs text-gray-500">
                      {reference.snippet +
                        (reference.snippet.length === 30 ? "..." : "")}
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="icon"
                    onClick={(e) => {
                      e.stopPropagation(); // 阻止事件冒泡
                      updateReferencePrimary({
                        dataset_id: searchParams.get("dataset_id") || "",
                        reference_id: reference.reference_id.toString(),
                        primary: reference.primary === 1 ? 0 : 1,
                      });
                    }}
                    className={cn(
                      reference.primary === 1 
                        ? "border-yellow-300 bg-yellow-50 text-yellow-600 hover:bg-yellow-100" 
                        : "text-gray-400 hover:text-yellow-500 hover:border-yellow-300"
                    )}
                    title={reference.primary === 1 ? "取消关键知识标记" : "标记为关键知识"}
                  >
                    <Star 
                      className={cn(
                        "w-4 h-4",
                        reference.primary === 1 ? "fill-current" : ""
                      )}
                    />
                  </Button>

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
                    <Trash className="w-4 h-4" />
                  </Button>
                </div>
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
