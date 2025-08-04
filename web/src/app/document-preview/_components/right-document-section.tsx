"use client";

import { FileTabs } from "./file-tabs";
import { DocumentSection } from "./document-section";
import { DocumentViewerRef } from "@/components/document-viewer";
import UploadDialog from "@/components/upload-dialog";
import { useDocumentPreviewStore } from "../model";
import { toast } from "sonner";
import { ReferenceSectionRef } from "./reference-section";

interface RightDocumentSectionProps {
  documentViewerRef: React.RefObject<DocumentViewerRef | null>;
  referenceSectionRef: React.RefObject<ReferenceSectionRef | null>;
  uploadDialogOpen: boolean;
  setUploadDialogOpen: (open: boolean) => void;
}

export function RightDocumentSection({
  documentViewerRef,
  referenceSectionRef,
  uploadDialogOpen,
  setUploadDialogOpen,
}: RightDocumentSectionProps) {
  const {
    selectFileId,
    referenceFileList,
    selectedQuestion,
    fileList,
    onFileSelect,
    addQuestionReference,
    addUploadFile,
    addReferenceFile,
  } = useDocumentPreviewStore();

  const onAddReferenceFile = (fileId: string) => {
    if (referenceFileList.find((file) => file.id === fileId)) {
      toast("已添加", {
        position: "top-center",
      });
      return;
    }
    const file = fileList.find((file) => file.id === fileId);
    if (file) {
      addReferenceFile(file);
    }
  };
  return (
    <div className="h-full bg-white border-l border-gray-200 overflow-hidden">
      <div className="flex flex-col h-full">
        <FileTabs
          selectFileId={selectFileId}
          onFileSelect={onFileSelect}
          referenceFileList={referenceFileList}
          onUploadClick={() => setUploadDialogOpen(true)}
        />
        <DocumentSection
          selectFileId={selectFileId}
          selectedQuestion={selectedQuestion}
          documentViewerRef={documentViewerRef}
          referenceSectionRef={referenceSectionRef}
          onAddQuestionReference={addQuestionReference}
        />
      </div>

      <UploadDialog
        open={uploadDialogOpen}
        onOpenChange={setUploadDialogOpen}
        fileList={fileList}
        referenceFileList={referenceFileList}
        onAddReferenceFile={onAddReferenceFile}
        onFileSelect={onFileSelect}
        onAddUploadFile={addUploadFile}
      />
    </div>
  );
}
