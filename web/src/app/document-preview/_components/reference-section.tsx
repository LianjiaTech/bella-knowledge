"use client";

import { Button } from "@/components/ui/button";
import { Trash } from "lucide-react";
import { QaReferenceList, Question, QaReference } from "@/lib/types/qa";
import { KnowledgeFile } from "@/lib/types/file";
import { DocumentViewerRef } from "@/components/document-viewer";

interface ReferenceSectionProps {
  selectedQuestion: Question | null;
  qaReferenceList: QaReferenceList;
  referenceFileList: KnowledgeFile[];
  selectFileId: string;
  documentViewerRef: React.RefObject<DocumentViewerRef | null>;
  onFileSelect: (fileId: string) => Promise<void>;
  onDeleteReference: (reference: QaReference["references"][0]) => void;
}

export function ReferenceSection({
  selectedQuestion,
  qaReferenceList,
  referenceFileList,
  selectFileId,
  documentViewerRef,
  onFileSelect,
  onDeleteReference,
}: ReferenceSectionProps) {
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
        {currentReferences.map((reference) => (
          <div
            key={reference.file_id + reference.path.join("-")}
            className="flex justify-between items-center border border-gray-200 rounded-md p-2 bg-white cursor-pointer hover:bg-gray-50 transition-colors"
            onClick={async () => {
              if (reference.file_id === selectFileId) {
                // 触发DocumentViewer组件高亮和滑动
                documentViewerRef.current?.scrollToAndHighlightNode(
                  reference.path.map((item) => Number(item)),
                );
                return;
              } else {
                await onFileSelect(reference.file_id);
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
                onDeleteReference(reference);
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
}
