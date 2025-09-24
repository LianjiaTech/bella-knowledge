"use client";

import React, { Suspense, useEffect, useRef, useState } from "react";
import TopBar from "./top-bar";
import { useSearchParams } from "next/navigation";
import { getFilePreviewUrl } from "@/request/files";
import { LeftSidebar } from "./_components/left-siderbar";
import { RagevalData } from "@/lib/types/rageval";
import DocumentViewer, {
  DocumentViewerRef,
} from "@/components/document-viewer";
import DragWidthBar from "@/components/drag-width-bar";
import { useLocalState } from "@/hooks/use-local-state";
import RagevalViewer from "@/components/rageval-viewer";

function RagEvalPreview() {
  const [ragevalData, setRagevalData] = useState<RagevalData[]>([]);
  const [selectedRagevalData, setSelectedRagevalData] =
    useState<RagevalData | null>(null);
  const [fileId, setFileId] = useState("");
  const [ragevalViewerWidth, setRagevalViewerWidth] = useLocalState(
    "ragevalViewerWidth",
    30,
  );

  const searchParams = useSearchParams();
  const documentViewerRef = useRef<DocumentViewerRef>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const getFile = async () => {
      try {
        const res = await getFilePreviewUrl(searchParams.get("fileId") || "");
        if (res) {
          const fetchFile = await fetch(res.url);
          if (!fetchFile.ok) {
            throw new Error("获取文件失败");
          }
          const text = await fetchFile.text();
          const data = text
            .split("\n")
            .filter((item) => item)
            .map((item) => JSON.parse(item));
          setRagevalData(data);
          if (data.length > 1) {
            setSelectedRagevalData(data[0]);
          }
        }
      } catch (error) {
        console.error(error);
      }
    };
    getFile();
  }, [searchParams]);

  const onClickReference = (reference: { file_id: string; path: string }) => {
    setFileId(reference.file_id);
    documentViewerRef.current?.scrollToAndHighlightNode(
      reference.path.split("/").filter(Boolean).map(Number),
    );
  };
  return (
    <>
      <TopBar />
      <LeftSidebar
        data={ragevalData}
        selectedRagevalData={selectedRagevalData}
        setSelectedRagevalData={setSelectedRagevalData}
      />
      <main ref={containerRef} className="pt-16 h-screen flex bg-gray-50">
        <RagevalViewer
          data={selectedRagevalData}
          width={ragevalViewerWidth}
          onClickReference={onClickReference}
        />
        {selectedRagevalData && (
          <>
            <DragWidthBar
              containerRef={containerRef}
              minWidthPercentage={20}
              maxWidthPercentage={80}
              localStorageKey="ragevalViewerWidth"
              width={ragevalViewerWidth}
              setWidth={setRagevalViewerWidth}
            />
            <div
              style={{
                width: `${100 - ragevalViewerWidth}%`,
              }}
            >
              <DocumentViewer
                className="flex-1"
                fileId={fileId}
                onClickNode={() => {}}
                ref={documentViewerRef}
              />
            </div>
          </>
        )}
      </main>
    </>
  );
}

export default function Page() {
  return (
    <Suspense>
      <RagEvalPreview />
    </Suspense>
  );
}
