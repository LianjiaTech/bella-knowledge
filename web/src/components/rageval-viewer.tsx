import { RagevalData } from "@/lib/types/rageval";
import { FileText, CheckCircle, AlertCircle } from "lucide-react";
import React from "react";
import { ScrollArea } from "./ui/scroll-area";
import { Badge } from "./ui/badge";

interface RagevalViewerProps {
  data: RagevalData | null;
  width?: number;
  onClickReference?: (reference: { file_id: string; path: string }) => void;
}
const RagevalViewer = ({
  data,
  width = 50,
  onClickReference,
}: RagevalViewerProps) => {
  return data ? (
    <div
      className="flex flex-col gap-4 flex-1 p-4 overflow-scroll scrollbar-hide"
      style={{
        width: `${width}%`,
      }}
    >
      <div className="bg-white rounded-xl p-6 border">
        <div className="flex items-center mb-4 gap-2">
          <FileText className="h-4 w-4" />
          <div className="font-semibold">原始问题</div>
        </div>
        <div className="border bg-gray-50 p-3 text-sm">{data.question}</div>
      </div>
      <div className="bg-white rounded-xl p-6 border flex flex-col overflow-hidden min-h-128">
        <div className="font-semibold mb-4">召回对比</div>
        <div className="flex gap-4 overflow-hidden">
          <div className="flex flex-1 flex-col">
            <div className="flex items-center gap-2 mb-3">
              <CheckCircle className="h-4 w-4 text-green-500" />
              <span className="text-sm font-medium">
                期望召回 ({data.gb_references.length}个)
              </span>
            </div>
            <ScrollArea className="flex-1 overflow-hidden">
              <div className="space-y-3">
                {data.gb_references.map((item, index) => {
                  const isRecall = data.reference.some(
                    (ref) =>
                      ref.metadata.file_id === item.file_id &&
                      ref.metadata.path === item.path,
                  );
                  return (
                    <div
                      key={index}
                      className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer hover:bg-gray-50 transition-colors ${
                        isRecall
                          ? "border-green-200 bg-green-50"
                          : "border-red-300 bg-red-50"
                      }`}
                      onClick={() => onClickReference?.(item)}
                    >
                      <div
                        className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium ${isRecall ? "bg-green-100 text-green-600" : "bg-red-100 text-red-600"}`}
                      >
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
                  );
                })}
              </div>
            </ScrollArea>
          </div>
          <div className="flex flex-1 flex-col">
            <div className="flex items-center gap-2 mb-3">
              <AlertCircle className="h-4 w-4 text-blue-500" />
              <span className="text-sm font-medium">
                实际召回 ({data.reference.length}个，召回率{" "}
                {(data.eval_recall * 100).toFixed(2)}
                %，精确率 {(data.eval_precision * 100).toFixed(2)}%)
              </span>
            </div>
            <ScrollArea className="flex-1 overflow-hidden">
              <div className="space-y-3">
                {data.reference.map((ref, index) => {
                  const isCorrectRecall = data.gb_references.some(
                    (gb) =>
                      gb.file_id === ref.metadata.file_id &&
                      gb.path === ref.metadata.path,
                  );
                  const isIncorrect = data.eval_incorrect_references.some(
                    (incorrect) => incorrect.path === ref.metadata.path,
                  );

                  return (
                    <div
                      key={index}
                      className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer hover:bg-gray-50 transition-colors ${
                        // 期望的召回
                        isCorrectRecall
                          ? "border-green-200 bg-green-50"
                          : isIncorrect
                            ? "border-red-300 bg-red-50"
                            : "border-yellow-300 bg-yellow-50"
                      }`}
                      onClick={() =>
                        onClickReference?.({
                          file_id: ref.metadata.file_id,
                          path: ref.metadata.path,
                        })
                      }
                    >
                      <div
                        className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium ${
                          isCorrectRecall
                            ? "bg-green-100 text-green-600"
                            : isIncorrect
                              ? "bg-red-100 text-red-600"
                              : "bg-yellow-100 text-yellow-600"
                        }`}
                      >
                        {index + 1}
                      </div>
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <Badge variant="outline" className="text-xs">
                            {ref.metadata.path}
                          </Badge>
                          {isCorrectRecall && (
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
          <div className="text-sm">{data?.groundtruth || ""}</div>
        </div>
        <div className="flex-1 bg-white rounded-xl p-6 border">
          <div className="flex items-center gap-2 mb-4">
            <AlertCircle className="h-4 w-4 text-blue-500" />
            <div className="font-semibold text-base">实际答案</div>
          </div>
          <div className="text-sm">{data?.response || ""}</div>
        </div>
      </div>
    </div>
  ) : (
    <div className="flex flex-1 items-center justify-center h-64 text-gray-500">
      未选择评测结果数据
    </div>
  );
};

export default RagevalViewer;
