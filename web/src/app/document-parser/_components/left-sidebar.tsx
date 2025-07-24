"use client";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { List, Loader2, Plus, X } from "lucide-react";
import React, { useEffect, useState } from "react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { useDocumentParserStore } from "../model";
import { Button } from "@/components/ui/button";
import UploadDialog from "@/components/upload-dialog";
import { useSearchParams } from "next/navigation";
import { toast } from "sonner";

const LeftSidebar = () => {
  const {
    fileList,
    datasetFileList,
    getFileList,
    selectedFile,
    selectDatasetFile,
    getDatasetFileList,
    addDatasetFile,
    addUploadFile,
    deleteDatasetFile,
  } = useDocumentParserStore();
  const [selectFileDialogOpen, setSelectFileDialogOpen] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [sheetLoading, setSheetLoading] = useState(false);
  const [getFileLoading, setGetFileLoading] = useState(false);

  const searchParams = useSearchParams();
  const datasetId = searchParams.get("dataset_id") || "";
  useEffect(() => {
    const init = async () => {
      setSheetLoading(true);
      await getFileList();
      await getDatasetFileList(datasetId);
      setSheetLoading(false);
    };
    init();
  }, [datasetId, getDatasetFileList, getFileList]);

  return (
    <div className="w-9">
      <Sheet
        open={sheetOpen}
        onOpenChange={async (open) => {
          if (open) {
            setSheetLoading(true);
            getDatasetFileList(datasetId).then(() => {
              setSheetLoading(false);
            });
          } else {
            setSheetOpen(false);
          }
          setSheetOpen(open);
        }}
      >
        <SheetTrigger>
          <List className="size-4 cursor-pointer ml-2 mt-2" />
        </SheetTrigger>
        <SheetContent className="flex flex-col w-80" side="left">
          <SheetHeader className="border-b">
            <SheetTitle className="flex items-center gap-2">
              <List className="size-4" />
              文件列表
              <span className="text-sm text-gray-500 ml-2">
                {datasetFileList.length}条
              </span>
              <Button
                variant="outline"
                className="ml-2"
                onClick={() => {
                  setGetFileLoading(true);
                  getFileList().then(() => {
                    setGetFileLoading(false);
                  });
                  setSelectFileDialogOpen(true);
                }}
              >
                <Plus className="size-4" />
              </Button>
            </SheetTitle>
          </SheetHeader>

          <ScrollArea className="flex-1 w-80 overflow-auto">
            <div className="h-full overflow-hidden pb-4">
              {sheetLoading ? (
                <div className="flex items-center justify-center h-full">
                  <Loader2 className="size-4 animate-spin" />
                </div>
              ) : datasetFileList.length === 0 ? (
                <div className="flex items-center justify-center h-full">
                  <span className="text-gray-500">暂无问题</span>
                </div>
              ) : (
                <div>
                  {datasetFileList.map((file) => {
                    return (
                      <div
                        className={`w-80 h-12 border-b border-gray-200 flex items-center px-3 cursor-pointer justify-between hover:bg-gray-50 transition-colors ${
                          selectedFile?.id === file.id
                            ? "bg-blue-100 rounded-md"
                            : ""
                        }`}
                        key={file.id}
                        onClick={async () => {
                          if (selectedFile?.id === file.id) {
                            return;
                          }
                          const res = await selectDatasetFile(file);
                          if (!res) {
                            toast.error("该文件无法解析");
                          } else {
                            toast.success(
                              "获取文件解析成功，预览原始文件可能需要等待几秒",
                            );
                          }
                        }}
                      >
                        <span className="flex-1 truncate text-sm">
                          {
                            fileList.find((f) => f.id === file.file_id)
                              ?.filename
                          }
                        </span>
                        <AlertDialog>
                          <AlertDialogTrigger
                            onClick={(e) => {
                              e.stopPropagation();
                            }}
                          >
                            <X className="size-4 flex-shrink-0 hover:text-red-500 transition-colors" />
                          </AlertDialogTrigger>
                          <AlertDialogContent>
                            <AlertDialogHeader>
                              <AlertDialogTitle>确定删除吗？</AlertDialogTitle>
                            </AlertDialogHeader>
                            <AlertDialogFooter>
                              <AlertDialogCancel>取消</AlertDialogCancel>
                              <AlertDialogAction
                                onClick={(e) => {
                                  e.stopPropagation();
                                  deleteDatasetFile(datasetId, file.file_id);
                                }}
                              >
                                确定
                              </AlertDialogAction>
                            </AlertDialogFooter>
                          </AlertDialogContent>
                        </AlertDialog>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </ScrollArea>
        </SheetContent>
      </Sheet>
      <UploadDialog
        open={selectFileDialogOpen}
        loading={getFileLoading}
        onOpenChange={setSelectFileDialogOpen}
        onAddUploadFile={addUploadFile}
        fileList={fileList}
        referenceFileList={datasetFileList}
        onAddReferenceFile={(fileId) => {
          addDatasetFile(datasetId, [fileId]);
        }}
        onSelectFile={() => {}}
      />
    </div>
  );
};

export default LeftSidebar;
