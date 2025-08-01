"use client";

import { QuestionAnswerSection } from "./question-answer-section";
import { ReferenceSection } from "./reference-section";
import { EmptyState } from "./empty-state";
import { DocumentViewerRef } from "@/components/document-viewer";
import { useDocumentPreviewStore } from "../model";
import { ReferenceSectionRef } from "./reference-section";

interface LeftContentSectionProps {
  documentViewerRef: React.RefObject<DocumentViewerRef | null>;
  referenceSectionRef: React.RefObject<ReferenceSectionRef | null>;
  width: number;
}

export function LeftContentSection({
  documentViewerRef,
  referenceSectionRef,
  width,
}: LeftContentSectionProps) {
  const { selectedQuestion } = useDocumentPreviewStore();
  return (
    <div
      className="h-full overflow-hidden flex flex-col bg-gray-100 relative"
      style={{
        width: `${width}%`,
      }}
    >
      <div className="flex flex-col gap-4 p-6 pt-4 overflow-auto scrollbar-hide">
        {selectedQuestion ? (
          <>
            <QuestionAnswerSection />
            <ReferenceSection
              documentViewerRef={documentViewerRef}
              ref={referenceSectionRef}
            />
          </>
        ) : (
          <EmptyState />
        )}
      </div>
    </div>
  );
}
