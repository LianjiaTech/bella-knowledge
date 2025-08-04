"use client";

import React, { Suspense, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useDocumentPreviewStore } from "./model";
import { DocumentViewerRef } from "@/components/document-viewer";
import { LeftContentSection } from "./_components/left-content-section";
import { RightDocumentSection } from "./_components/right-document-section";
import { LeftSidebar } from "./_components/left-siderbar";
import TopBar from "./top-bar";
import { ReferenceSectionRef } from "./_components/reference-section";
import DragWidthBar from "@/components/drag-width-bar";
import { useLocalState } from "@/hooks/use-local-state";

function DocumentPreviewPage() {
  const searchParams = useSearchParams();

  const [uploadDialogOpen, setUploadDialogOpen] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [leftSectionWidth, setLeftSectionWidth] = useLocalState(
    "leftSectionWidth",
    50,
  );

  const { lastEditTime, initPage, clear, initReferenceFileList } =
    useDocumentPreviewStore();

  const documentViewerRef = useRef<DocumentViewerRef>(null);
  const referenceSectionRef = useRef<ReferenceSectionRef>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const datasetId = searchParams.get("dataset_id") || "";

    const init = async () => {
      await initPage(datasetId);
      // 移除自动打开边栏，现在通过鼠标悬停打开
      initReferenceFileList(datasetId);
    };
    init();
    return () => {
      clear();
    };
  }, [initPage, searchParams, clear, initReferenceFileList]);

  return (
    <>
      <TopBar lastEditTime={lastEditTime} />
      <LeftSidebar open={sidebarOpen} onOpenChange={setSidebarOpen} />
      <main className="pt-16 h-screen flex" ref={containerRef}>
        {/* 左侧内容区域 - 撑满剩余宽度 */}
        <LeftContentSection
          documentViewerRef={documentViewerRef}
          referenceSectionRef={referenceSectionRef}
          width={leftSectionWidth}
        />
        <DragWidthBar
          containerRef={containerRef}
          minWidthPercentage={20}
          maxWidthPercentage={60}
          localStorageKey="leftSectionWidth"
          width={leftSectionWidth}
          setWidth={setLeftSectionWidth}
        />
        {/* 右侧文档区域 - 固定在右侧 */}
        <div
          style={{
            width: `${100 - leftSectionWidth}%`,
          }}
        >
          <RightDocumentSection
            documentViewerRef={documentViewerRef}
            referenceSectionRef={referenceSectionRef}
            uploadDialogOpen={uploadDialogOpen}
            setUploadDialogOpen={setUploadDialogOpen}
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
