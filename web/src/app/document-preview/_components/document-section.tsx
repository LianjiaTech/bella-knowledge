"use client";

import DocumentViewer, {
  DocumentViewerRef,
} from "@/components/document-viewer";
import { DocumentNode } from "@/lib/types/documents";
import { Question } from "@/lib/types/qa";
import { useSearchParams } from "next/navigation";
import { useRef } from "react";
import { ReferenceSectionRef } from "./reference-section";

interface DocumentSectionProps {
  selectFileId: string;
  selectedQuestion: Question | null;
  documentViewerRef: React.RefObject<DocumentViewerRef | null>;
  referenceSectionRef: React.RefObject<ReferenceSectionRef | null>;
  onAddQuestionReference: (params: {
    dataset_id: string;
    item_id: string;
    file_id: string;
    path: number[];
    snippet: string;
  }) => Promise<void>;
}

export function DocumentSection({
  selectFileId,
  selectedQuestion,
  documentViewerRef,
  referenceSectionRef,
  onAddQuestionReference,
}: DocumentSectionProps) {
  const searchParams = useSearchParams();
  const onClickNode = (node: DocumentNode) => {
    if (referenceSectionRef.current) {
      referenceSectionRef.current.scrollToAndHighlightNode(node.path);
    }
  };
  const loadingRef = useRef(false);

  const onDoubleClickNode = async (node: DocumentNode) => {
    if (selectedQuestion) {
      if (loadingRef.current) {
        return;
      }
      loadingRef.current = true;
      const datasetId = searchParams.get("dataset_id") || "";

      await onAddQuestionReference({
        dataset_id: datasetId,
        item_id: selectedQuestion?.item_id.toString() || "",
        file_id: selectFileId || "",
        path: node.path,
        snippet:
          node.element.type !== "Figure"
            ? (node.element.text || "").slice(0, 30)
            : "",
      });
      loadingRef.current = false;
      if (referenceSectionRef.current) {
        referenceSectionRef.current.scrollToAndHighlightNode(node.path);
      }
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
