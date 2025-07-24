"use client";

import { FileTabs } from "./file-tabs";
import { DocumentSection } from "./document-section";
import { Question } from "@/lib/types/qa";
import { KnowledgeFile } from "@/lib/types/file";
import { DocumentViewerRef } from "@/components/document-viewer";
import UploadDialog from "@/components/upload-dialog";

interface RightDocumentSectionProps {
  referenceFileList: KnowledgeFile[];
  selectFileId: string;
  selectedQuestion: Question | null;
  documentViewerRef: React.RefObject<DocumentViewerRef | null>;
  uploadDialogOpen: boolean;
  setUploadDialogOpen: (open: boolean) => void;
  fileList: KnowledgeFile[];
  datasetId: string;
  onFileSelect: (fileId: string) => Promise<void>;
  onAddReferenceFile: (fileId: string) => void;
  onAddQuestionReference: (params: {
    dataset_id: string;
    item_id: string;
    file_id: string;
    path: number[];
  }) => Promise<void>;
  onAddUploadFile: (file: KnowledgeFile) => void;
}

export function RightDocumentSection({
  referenceFileList,
  selectFileId,
  selectedQuestion,
  documentViewerRef,
  uploadDialogOpen,
  setUploadDialogOpen,
  fileList,
  datasetId,
  onFileSelect,
  onAddReferenceFile,
  onAddQuestionReference,
  onAddUploadFile,
}: RightDocumentSectionProps) {
  return (
    <div className="h-full bg-white border-l border-gray-200 px-4 py-6 overflow-hidden">
      <div className="flex flex-col h-full">
        <div className="text-base font-bold mb-4">知识文档</div>

        <FileTabs
          selectFileId={selectFileId}
          onFileSelect={onFileSelect}
          referenceFileList={referenceFileList}
          onUploadClick={() => setUploadDialogOpen(true)}
        />

        <div className="flex-1 overflow-hidden">
          <DocumentSection
            selectFileId={selectFileId}
            selectedQuestion={selectedQuestion}
            documentViewerRef={documentViewerRef}
            onAddQuestionReference={onAddQuestionReference}
            datasetId={datasetId}
          />
        </div>
      </div>

      <UploadDialog
        open={uploadDialogOpen}
        onOpenChange={setUploadDialogOpen}
        fileList={fileList}
        referenceFileList={referenceFileList}
        onAddReferenceFile={onAddReferenceFile}
        onSelectFile={onFileSelect}
        onAddUploadFile={onAddUploadFile}
      />
    </div>
  );
}
