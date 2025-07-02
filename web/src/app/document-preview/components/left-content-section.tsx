"use client";

import { Menu } from "lucide-react";
import { Button } from "@/components/ui/button";
import { QuestionAnswerSection } from "./question-answer-section";
import { ReferenceSection } from "./reference-section";
import { EmptyState } from "./empty-state";
import { Question, QaReferenceList, QaReference } from "@/lib/types/qa";
import { KnowledgeFile } from "@/lib/types/file";
import { DocumentViewerRef } from "@/components/document-viewer";

interface LeftContentSectionProps {
  selectedQuestion: Question | null;
  questionInputVal: string;
  answerInputVal: string;
  qaReferenceList: QaReferenceList;
  referenceFileList: KnowledgeFile[];
  selectFileId: string;
  documentViewerRef: React.RefObject<DocumentViewerRef | null>;
  onQuestionChange: (value: string) => void;
  onAnswerChange: (value: string) => void;
  onTextAreaBlur: () => void;
  onFileSelect: (fileId: string) => Promise<void>;
  onDeleteReference: (reference: QaReference["references"][0]) => void;
  onOpenSidebar: () => void;
}

export function LeftContentSection({
  selectedQuestion,
  questionInputVal,
  answerInputVal,
  qaReferenceList,
  referenceFileList,
  selectFileId,
  documentViewerRef,
  onQuestionChange,
  onAnswerChange,
  onTextAreaBlur,
  onFileSelect,
  onDeleteReference,
  onOpenSidebar,
}: LeftContentSectionProps) {
  return (
    <div className="flex-1 h-full overflow-hidden flex flex-col bg-gray-100 relative">
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
            <QuestionAnswerSection
              selectedQuestion={selectedQuestion}
              questionInputVal={questionInputVal}
              answerInputVal={answerInputVal}
              onQuestionChange={onQuestionChange}
              onAnswerChange={onAnswerChange}
              onBlur={onTextAreaBlur}
            />
            <ReferenceSection
              selectedQuestion={selectedQuestion}
              qaReferenceList={qaReferenceList}
              referenceFileList={referenceFileList}
              selectFileId={selectFileId}
              documentViewerRef={documentViewerRef}
              onFileSelect={onFileSelect}
              onDeleteReference={onDeleteReference}
            />
          </>
        ) : (
          <EmptyState />
        )}
      </div>
    </div>
  );
}
