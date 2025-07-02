"use client";

import { useRef } from "react";
import { KnowledgeFile } from "@/lib/types/file";
import { Button } from "@/components/ui/button";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";
import { Plus } from "lucide-react";

interface FileTabsProps {
  referenceFileList: KnowledgeFile[];
  selectFileId: string;
  onFileSelect: (fileId: string) => Promise<void>;
  onUploadClick: () => void;
}

export function FileTabs({
  referenceFileList,
  selectFileId,
  onFileSelect,
  onUploadClick,
}: FileTabsProps) {
  const tabsScrollRef = useRef<HTMLDivElement>(null);

  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 overflow-hidden relative">
        <div className="flex items-center border-b border-gray-200 bg-gray-50 rounded-t-md">
          <ScrollArea ref={tabsScrollRef} className="sm:w-[520px] lg:w-[720px]">
            <div className="flex w-max min-h-[40px]">
              {referenceFileList.map((file) => (
                <div
                  key={file.id}
                  className={`
                  flex items-center gap-2 px-3 py-2 cursor-pointer border-r border-gray-200 
                  transition-colors duration-200 group flex-shrink-0 w-[180px]
                  ${
                    selectFileId === file.id
                      ? "bg-white border-b-2 border-b-blue-500 text-blue-600"
                      : "hover:bg-gray-100 text-gray-700"
                  }
                `}
                  onClick={() => onFileSelect(file.id)}
                  title={file.filename}
                >
                  <span className="text-sm font-medium truncate flex-1">
                    {file.filename}
                  </span>
                </div>
              ))}

              {/* 如果没有文件，显示提示 */}
              {referenceFileList.length === 0 && (
                <div className="flex items-center px-3 py-2 text-gray-500 text-sm">
                  暂无文件，请上传文档
                </div>
              )}
            </div>
            <ScrollBar orientation="horizontal" />
          </ScrollArea>
        </div>
      </div>

      {/* 上传按钮 */}
      <Button
        variant="outline"
        size="sm"
        onClick={onUploadClick}
        className="flex-shrink-0"
        title="上传文档"
      >
        <Plus size={16} />
      </Button>
    </div>
  );
}
