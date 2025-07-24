"use client";

import React, { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogClose,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "sonner";
import { Check, Loader2, Plus } from "lucide-react";
import { DatasetFile, KnowledgeFile } from "@/lib/types/file";
import { getFileProgress, postUploadFile } from "@/request/files";

interface UploadDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  loading?: boolean;
  fileList: KnowledgeFile[];
  referenceFileList: KnowledgeFile[] | DatasetFile[];
  onAddReferenceFile: (fileId: string) => void;
  onSelectFile: (fileId: string) => void;
  onAddUploadFile: (file: KnowledgeFile) => void;
}

const UploadDialog: React.FC<UploadDialogProps> = ({
  open,
  onOpenChange,
  loading,
  fileList,
  referenceFileList,
  onAddReferenceFile,
  onSelectFile,
  onAddUploadFile,
}) => {
  const [dragActive, setDragActive] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [parsing, setParsing] = useState(false);

  const handleDrop = async (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      await handleFileUpload(e.dataTransfer.files[0]);
    }
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragActive(true);
  };

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragActive(false);
  };
  // 实现轮训，直到解析完成
  const pollingFileProgress = async (fileId: string) => {
    return new Promise((resolve) => {
      const poll = async () => {
        const res = await getFileProgress(fileId);
        if (res?.status === "document_parse_finish") {
          resolve(true);
        } else {
          setTimeout(() => poll(), 1000);
        }
      };
      poll();
    });
  };
  const handleFileUpload = async (file: File) => {
    setUploading(true);
    const fileData = await postUploadFile(file);

    const fileId = fileData?.id || "";
    if (fileData && fileId) {
      setUploading(false);
      setParsing(true);
      await pollingFileProgress(fileId);
      setParsing(false);
      onAddUploadFile(fileData);
      onAddReferenceFile(fileId);
      onSelectFile(fileId);
      toast.success("上传成功，已自动选中");
    } else {
      setUploading(false);
      setParsing(false);
      toast.error("上传失败");
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      await handleFileUpload(e.target.files[0]);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="min-w-[800px] max-h-[80vh]">
        <DialogHeader>
          <DialogTitle>文档管理</DialogTitle>
        </DialogHeader>
        <div className="flex gap-6 p-4">
          {/* Upload Section */}
          <div className="flex-1">
            <div className="text-base font-bold mb-4">已选文档</div>
            <div className="flex flex-col gap-2 max-h-[500px] overflow-y-auto [&::-webkit-scrollbar]:hidden">
              {referenceFileList.map((file) => (
                <div
                  key={file.id}
                  className="flex justify-between items-center border border-gray-200 rounded-md p-3 hover:bg-gray-50 transition-colors"
                >
                  <span className="flex-1 text-sm">
                    {"filename" in file
                      ? file.filename
                      : fileList.find((f) => f.id === file.file_id)?.filename}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* File List Section */}
          <div className="flex-1">
            <div className="text-base font-bold mb-4">可选文档</div>
            <div
              className={`mb-4 border-2 border-dashed rounded-md p-8 text-center transition-colors ${
                dragActive ? "border-blue-500 bg-blue-50" : "border-gray-300"
              }`}
              onDrop={handleDrop}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onClick={() =>
                !uploading && !parsing && fileInputRef.current?.click()
              }
              style={{ cursor: uploading || parsing ? "default" : "pointer" }}
            >
              <input
                ref={fileInputRef}
                accept=".pdf,.doc,.docx"
                type="file"
                className="hidden"
                onChange={handleFileChange}
                disabled={uploading || parsing}
              />
              {uploading ? (
                <Spinner size="md">上传中...</Spinner>
              ) : parsing ? (
                <Spinner size="md">解析中...</Spinner>
              ) : (
                <div className="text-gray-500 mb-2">
                  拖拽文件到此处，或{" "}
                  <span className="text-blue-600 underline">点击选择文件</span>
                </div>
              )}
            </div>
            <div className="flex flex-col gap-2 max-h-[400px] overflow-y-auto [&::-webkit-scrollbar]:hidden">
              {loading ? (
                <div className="flex items-center justify-center h-full">
                  <Loader2 className="size-4 animate-spin" />
                </div>
              ) : (
                (fileList || []).map((file) => {
                  // 判断referenceFileList是否为DatasetFile[]
                  const isDatasetFile = referenceFileList.every(
                    (f) => "file_id" in f,
                  );
                  const isSelected = isDatasetFile
                    ? referenceFileList.find((f) => f.file_id === file.id)
                    : referenceFileList.find((f) => f.id === file.id);
                  return (
                    <div
                      key={file.id}
                      className="flex justify-between items-center border border-gray-200 rounded-md p-3 hover:bg-gray-50 transition-colors"
                    >
                      <span className="flex-1 text-sm line-clamp-1 text-ellipsis max-w-[220px] break-all">
                        {file.filename}
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          if (!isSelected) {
                            onAddReferenceFile(file.id);
                            onSelectFile(file.id);
                          }
                        }}
                      >
                        {isSelected ? (
                          <Check className="w-4 h-4" />
                        ) : (
                          <Plus className="w-4 h-4" />
                        )}
                        {isSelected ? "已选择" : "选择"}
                      </Button>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>
        <DialogClose asChild>
          <Button className="mt-4 w-full" variant="secondary">
            关闭
          </Button>
        </DialogClose>
      </DialogContent>
    </Dialog>
  );
};

export default UploadDialog;
