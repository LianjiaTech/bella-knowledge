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
import { AlertCircle, CheckCircle, FileText } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import DragWidthBar from "@/components/drag-width-bar";
import { useLocalState } from "@/hooks/use-local-state";

function RagEvalPreview() {
  const [ragevalData, setRagevalData] = useState<RagevalData[]>([]);
  const [error, setError] = useState("");
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
        setError(error as string);
      }
    };
    getFile();
  }, [searchParams]);

  const onClickReference = (reference: { file_id: string; path: string }) => {
    setFileId(reference.file_id);
    documentViewerRef.current?.scrollToAndHighlightNode(
      reference.path.split("/").map(Number),
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
        {selectedRagevalData ? (
          <div
            className="flex flex-col gap-4 flex-1 p-4 overflow-scroll scrollbar-hide"
            style={{
              width: `${ragevalViewerWidth}%`,
            }}
          >
            <div className="bg-white rounded-xl p-6 border">
              <div className="flex items-center mb-4 gap-2">
                <FileText className="h-4 w-4" />
                <div className="font-semibold">测试问题</div>
              </div>
              <div className="border bg-gray-50 p-3 text-sm">
                {selectedRagevalData.question}
              </div>
            </div>
            <div className="bg-white rounded-xl p-6 border flex flex-col overflow-hidden min-h-128">
              <div className="font-semibold mb-4">召回对比</div>
              <div className="flex gap-4 overflow-hidden">
                <div className="flex flex-1 flex-col">
                  <div className="flex items-center gap-2 mb-3">
                    <CheckCircle className="h-4 w-4 text-green-500" />
                    <span className="text-sm font-medium">
                      期望召回 ({selectedRagevalData.gb_references.length}个)
                    </span>
                  </div>
                  <ScrollArea className="flex-1 overflow-hidden">
                    <div className="space-y-3">
                      {selectedRagevalData.gb_references.map((item, index) => (
                        <div
                          key={index}
                          className="flex items-start gap-3 p-3 rounded-lg border cursor-pointer hover:bg-gray-50 transition-colors border-green-200 bg-green-50"
                          onClick={() => onClickReference(item)}
                        >
                          <div className="flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium bg-green-100 text-green-600">
                            {index + 1}
                          </div>
                          <div className="flex flex-col items-start">
                            <span className="inline-flex items-center rounded-full border px-2.5 py-0.5 font-semibold transition-colors focus:outline-hidden focus:ring-2 focus:ring-ring focus:ring-offset-2 text-foreground text-xs">
                              {item.path}
                            </span>
                            <span className="text-xs text-gray-600">
                              文件ID:{item.file_id}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </ScrollArea>
                </div>
                <div className="flex flex-1 flex-col">
                  <div className="flex items-center gap-2 mb-3">
                    <AlertCircle className="h-4 w-4 text-blue-500" />
                    <span className="text-sm font-medium">
                      实际召回 ({selectedRagevalData.reference.length}个)
                    </span>
                  </div>
                  <ScrollArea className="flex-1 overflow-hidden">
                    <div className="space-y-3">
                      {selectedRagevalData.reference.map((ref, index) => {
                        const isCorrect =
                          selectedRagevalData.gb_references.some(
                            (gb) => gb.path === ref.metadata.path,
                          );
                        const isIncorrect =
                          selectedRagevalData.eval_incorrect_references.some(
                            (incorrect) => incorrect.path === ref.metadata.path,
                          );

                        return (
                          <div
                            key={index}
                            className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer hover:bg-gray-50 transition-colors ${
                              isCorrect
                                ? "border-green-200 bg-green-50"
                                : isIncorrect
                                  ? "border-red-300 bg-red-50"
                                  : "border-gray-200 bg-gray-50"
                            }`}
                            onClick={() =>
                              onClickReference({
                                file_id: ref.metadata.file_id,
                                path: ref.metadata.path,
                              })
                            }
                          >
                            <div
                              className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium ${
                                isCorrect
                                  ? "bg-green-100 text-green-600"
                                  : isIncorrect
                                    ? "bg-red-100 text-red-600"
                                    : "bg-gray-100 text-gray-600"
                              }`}
                            >
                              {index + 1}
                            </div>
                            <div className="flex-1">
                              <div className="flex items-center gap-2 mb-1">
                                <Badge variant="outline" className="text-xs">
                                  {ref.metadata.path}
                                </Badge>
                                {isCorrect && (
                                  <Badge className="bg-green-100 text-green-700 text-xs">
                                    正确
                                  </Badge>
                                )}
                                {isIncorrect && (
                                  <Badge className="bg-red-100 text-red-700 text-xs">
                                    错召回
                                  </Badge>
                                )}
                              </div>
                              <p className="text-xs text-gray-600 line-clamp-2 mb-1">
                                {ref.text}
                              </p>
                              <div className="flex items-center gap-2">
                                <Badge variant="secondary" className="text-xs">
                                  {ref.metadata.tokens} tokens
                                </Badge>
                                <Badge variant="outline" className="text-xs">
                                  Score: {ref.score}
                                </Badge>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </ScrollArea>
                </div>
              </div>
            </div>
            <div className="flex flex-wrap gap-4">
              <div className="flex-1 bg-white rounded-xl p-6 border">
                <div className="flex items-center gap-2 mb-4">
                  <CheckCircle className="h-4 w-4 text-green-500" />
                  <div className="font-semibold text-base">期望答案</div>
                </div>
                <div className="text-sm">
                  {selectedRagevalData?.groundtruth || ""}
                </div>
              </div>
              <div className="flex-1 bg-white rounded-xl p-6 border">
                <div className="flex items-center gap-2 mb-4">
                  <AlertCircle className="h-4 w-4 text-blue-500" />
                  <div className="font-semibold text-base">实际回答</div>
                </div>
                <div className="text-sm">
                  {selectedRagevalData?.response || ""}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex flex-1 items-center justify-center h-64 text-gray-500">
            未选择评测结果数据
          </div>
        )}
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
