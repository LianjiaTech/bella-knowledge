"use client";

import { Menu } from "lucide-react";
import { Button } from "@/components/ui/button";
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
  onOpenSidebar: () => void;
}

export function LeftContentSection({
  documentViewerRef,
  referenceSectionRef,
  width,
  onOpenSidebar,
}: LeftContentSectionProps) {
  const { selectedQuestion } = useDocumentPreviewStore();
  return (
    <div
      className="h-full overflow-hidden flex flex-col bg-gray-100 relative"
      style={{
        width: `${width}%`,
      }}
    >
      <div className="absolute top-4 left-4 z-10">
        <Button
          variant="outline"
          size="icon"
          onClick={onOpenSidebar}
          className="h-8 w-8"
        >
          <Menu size={16} />
        </Button>
      </div>
      <div className="flex flex-col gap-4 p-6 pt-16 overflow-auto scrollbar-hide">
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
