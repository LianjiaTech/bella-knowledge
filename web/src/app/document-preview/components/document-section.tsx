"use client";

import DocumentViewer, {
  DocumentViewerRef,
} from "@/components/document-viewer";
import { DocumentNode } from "@/lib/types/documents";
import { Question } from "@/lib/types/qa";
import { useRef } from "react";

interface DocumentSectionProps {
  selectFileId: string;
  selectedQuestion: Question | null;
  documentViewerRef: React.RefObject<DocumentViewerRef | null>;
  onAddQuestionReference: (params: {
    dataset_id: string;
    item_id: string;
    file_id: string;
    path: number[];
  }) => Promise<void>;
  datasetId: string;
}

export function DocumentSection({
  selectFileId,
  selectedQuestion,
  documentViewerRef,
  onAddQuestionReference,
  datasetId,
}: DocumentSectionProps) {
  const onClickNode = () => {
    // console.log(node);
  };
  const loadingRef = useRef(false);
  const onDoubleClickNode = async (node: DocumentNode) => {
    if (selectedQuestion) {
      if (loadingRef.current) {
        return;
      }
      loadingRef.current = true;
      await onAddQuestionReference({
        dataset_id: datasetId,
        item_id: selectedQuestion?.item_id.toString() || "",
        file_id: selectFileId || "",
        path: node.path,
      });
      loadingRef.current = false;
    }
  };

  return (
    <div className="overflow-hidden h-[calc(100vh-200px)]">
      <DocumentViewer
        fileId={selectFileId}
        onClickNode={onClickNode}
        onDoubleClickNode={onDoubleClickNode}
        ref={documentViewerRef}
      />
    </div>
  );
}
