"use client";

import React, { Suspense, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useDocumentPreviewStore } from "./model";
import { toast } from "sonner";
import { DocumentViewerRef } from "@/components/document-viewer";
import { LeftContentSection } from "./_components/left-content-section";
import { RightDocumentSection } from "./_components/right-document-section";
import { QaReference } from "@/lib/types/qa";
import { LeftSidebar } from "./_components/left-siderbar";
import TopBar from "./top-bar";
import { KnowledgeFile } from "@/lib/types/file";

function DocumentPreviewPage() {
  const searchParams = useSearchParams();
  const datasetId = searchParams.get("dataset_id") || "";

  const [uploadDialogOpen, setUploadDialogOpen] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const {
    questionList,
    fileList,
    referenceFileList,
    selectedQuestion,
    qaReferenceList,
    lastEditTime,
    initPage,
    questionInputVal,
    answerInputVal,
    selectFileId,
    setSelectFileId,
    onChangeQuestionInputVal,
    onChangeAnswerInputVal,
    addQuestion,
    updateQuestion,
    deleteQuestion,
    onChangeSelectedQuestion,

    addQuestionReference,
    deleteQuestionReference,

    addUploadFile,
    addReferenceFile,
    clear,
    initLoading,
    initReferenceFileList,
  } = useDocumentPreviewStore();

  const documentViewerRef = useRef<DocumentViewerRef>(null);

  useEffect(() => {
    const init = async () => {
      await initPage(datasetId);
      setSidebarOpen(true);
      initReferenceFileList(datasetId);
    };
    init();
    return () => {
      clear();
    };
  }, [initPage, datasetId, clear, initReferenceFileList]);

  const handleTextAreaBlur = () => {
    if (selectedQuestion) {
      updateQuestion({
        ...selectedQuestion,
        question: questionInputVal,
        answer: answerInputVal,
      });
    }
  };

  const onFileSelect = async (value: string) => {
    setSelectFileId(value);
  };

  const handleAddReferenceFile = (fileId: string) => {
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

  const handleDeleteReference = (reference: QaReference["references"][0]) => {
    deleteQuestionReference({
      dataset_id: datasetId,
      reference_id: reference.reference_id,
    });
  };

  const handleAddQuestionReference = async (params: {
    dataset_id: string;
    item_id: string;
    file_id: string;
    path: number[];
  }) => {
    await addQuestionReference(params);
  };

  const handleAddUploadFile = (file: KnowledgeFile) => {
    addUploadFile(file);
  };

  return (
    <>
      <TopBar lastEditTime={lastEditTime} />
      <LeftSidebar
        loading={initLoading}
        questionList={questionList}
        selectedQuestion={selectedQuestion}
        datasetId={datasetId}
        open={sidebarOpen}
        onOpenChange={setSidebarOpen}
        onChangeSelectedQuestion={onChangeSelectedQuestion}
        onDeleteQuestion={deleteQuestion}
        onAddQuestion={addQuestion}
      />
      <main className="pt-16 h-screen flex">
        {/* 左侧内容区域 - 撑满剩余宽度 */}
        <LeftContentSection
          selectedQuestion={selectedQuestion}
          questionInputVal={questionInputVal}
          answerInputVal={answerInputVal}
          qaReferenceList={qaReferenceList}
          referenceFileList={referenceFileList}
          selectFileId={selectFileId}
          documentViewerRef={documentViewerRef}
          onQuestionChange={onChangeQuestionInputVal}
          onAnswerChange={onChangeAnswerInputVal}
          onTextAreaBlur={handleTextAreaBlur}
          onFileSelect={onFileSelect}
          onDeleteReference={handleDeleteReference}
          onOpenSidebar={() => setSidebarOpen(true)}
        />

        {/* 右侧文档区域 - 固定在右侧 */}
        <div className="h-[calc(100vh-4rem)] sm:w-[600px] lg:w-[900px]">
          <RightDocumentSection
            referenceFileList={referenceFileList}
            selectFileId={selectFileId}
            selectedQuestion={selectedQuestion}
            documentViewerRef={documentViewerRef}
            uploadDialogOpen={uploadDialogOpen}
            setUploadDialogOpen={setUploadDialogOpen}
            fileList={fileList}
            datasetId={datasetId}
            onFileSelect={onFileSelect}
            onAddReferenceFile={handleAddReferenceFile}
            onAddQuestionReference={handleAddQuestionReference}
            onAddUploadFile={handleAddUploadFile}
          />
        </div>
      </main>
    </>
  );
}

export default function Page() {
  return (
    <Suspense>
      <DocumentPreviewPage />
    </Suspense>
  );
}
