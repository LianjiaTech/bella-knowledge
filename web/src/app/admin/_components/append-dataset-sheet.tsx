"use client";

import { useState, useCallback } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Upload, FileSpreadsheet, X, AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils";
import { webRequest } from "@/lib/request/web";
import { toast } from "sonner";
import { postUploadFile } from "@/request/files";

interface AppendDatasetSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  datasetName: string;
  datasetId: string;
}

export function AppendDatasetSheet({
  open,
  onOpenChange,
  datasetId,
}: AppendDatasetSheetProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const acceptedExtensions = [".xlsx", ".xls"];

  // 处理文件选择
  const handleFileSelect = useCallback((files: FileList | null) => {
    if (!files || files.length === 0) return;

    const file = files[0];

    setSelectedFile(file);
    setError(null);
  }, []);

  // 拖拽处理
  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragOver(false);
      handleFileSelect(e.dataTransfer.files);
    },
    [handleFileSelect],
  );

  // 移除文件
  const handleRemoveFile = () => {
    setSelectedFile(null);
    setError(null);
  };

  // 追加数据集
  const handleAppend = async () => {
    if (!selectedFile) return;

    try {
      setIsUploading(true);

      // Add UUID to filename for dataset import
      const uuid = crypto.randomUUID();
      const lastDotIndex = selectedFile.name.lastIndexOf('.');
      const newFilename = lastDotIndex === -1 
        ? `${selectedFile.name}(${uuid})`
        : `${selectedFile.name.substring(0, lastDotIndex)}(${uuid})${selectedFile.name.substring(lastDotIndex)}`;
      
      const fileWithUUID = new File([selectedFile], newFilename, {
        type: selectedFile.type,
        lastModified: selectedFile.lastModified,
      });

      const data = await postUploadFile({
        file: fileWithUUID,
        purpose: "datasets_import",
      });
      if (data) {
        const fileId = data.id;
        const res = await webRequest({
          path: `/api/dataset`,
          method: "POST",
          body: {
            file_id: fileId,
            dataset_id: datasetId,
          },
        });
        if (res.code === 200) {
          toast.success("追加成功", {
            position: "top-center",
          });
          onOpenChange(false);
          setSelectedFile(null);
        } else {
          setError(res.message);
        }
      }
    } catch (error) {
      setError(error instanceof Error ? error.message : "追加失败，请重试");
    } finally {
      setIsUploading(false);
    }
  };

  // 格式化文件大小
  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-[400px]">
        <SheetHeader>
          <SheetTitle>追加数据集</SheetTitle>
        </SheetHeader>
        <div className="px-4">
          {/* 文件上传区域 */}
          <div>
            <h3 className="text-base font-medium mb-2">选择 Excel 文件</h3>
            <p className="text-sm text-gray-600 mb-4">
              支持 .xlsx 和 .xls 格式，文件大小不超过 50MB
            </p>
          </div>

          {!selectedFile ? (
            <div
              className={cn(
                "border-2 border-dashed rounded-lg p-8 text-center transition-colors cursor-pointer",
                isDragOver
                  ? "border-blue-400 bg-blue-50"
                  : "border-gray-300 hover:border-gray-400",
              )}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => {
                const input = document.createElement("input");
                input.type = "file";
                input.accept = acceptedExtensions.join(",");
                input.onchange = (e) => {
                  const target = e.target as HTMLInputElement;
                  handleFileSelect(target.files);
                };
                input.click();
              }}
            >
              <Upload className="mx-auto h-12 w-12 text-gray-400 mb-4" />
              <div className="space-y-2">
                <p className="text-base font-medium">
                  点击选择文件或拖拽文件到这里
                </p>
                <p className="text-sm text-gray-500">支持 .xlsx、.xls 格式</p>
              </div>
            </div>
          ) : (
            <div className="border rounded-lg p-4 bg-gray-50">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <FileSpreadsheet className="h-8 w-8 text-green-600" />
                  <div>
                    <p className="font-medium text-sm">{selectedFile.name}</p>
                    <p className="text-xs text-gray-500">
                      {formatFileSize(selectedFile.size)}
                    </p>
                  </div>
                </div>
                {!isUploading && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleRemoveFile}
                    className="text-gray-500 hover:text-red-600"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                )}
              </div>
            </div>
          )}

          {/* 错误提示 */}
          {error && (
            <div className="rounded-md bg-red-50 p-4">
              <div className="flex">
                <div className="flex-shrink-0">
                  <AlertCircle className="h-5 w-5 text-red-400" />
                </div>
                <div className="ml-3">
                  <p className="text-sm text-red-800">{error}</p>
                </div>
              </div>
            </div>
          )}

          {/* 操作按钮 */}
          <div className="flex justify-end space-x-3 pt-6">
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isUploading}
            >
              取消
            </Button>
            <Button
              onClick={handleAppend}
              disabled={!selectedFile || isUploading}
              className="min-w-[80px]"
            >
              {isUploading ? "追加中..." : "追加"}
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
