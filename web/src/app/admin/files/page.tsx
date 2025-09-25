"use client";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useModel } from "./model";
import { ArrowLeftIcon } from "lucide-react";
import { KnowledgeFile } from "@/lib/types/file";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DataTable } from "./_components/data-table";
import { getColumns } from "./_components/columns";
import { z } from "zod";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
} from "@/components/ui/form";
import { useThrottleFn } from "ahooks";
import { getFilePreviewUrl } from "@/request/files";
import { toast } from "sonner";
import dynamic from "next/dynamic";
import { useUserStore } from "@/store/user";
import { useRouter } from "next/navigation";

const FileViewer = dynamic(() => import("@/components/file-viewer"), {
  ssr: false,
});

const createFolderFormSchema = z.object({
  name: z.string(),
});

const Page = () => {
  const {
    files,
    currentDirStack,
    tableLoading,
    enterFolder,
    jumpFolder,
    initPage,
    createFolder,
    backFolder,
    uploadFile,
    renameFile,
    deleteFile,
    reUploadFile,
  } = useModel();
  const currentDir = currentDirStack[currentDirStack.length - 1];
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [open, setOpen] = useState(false);
  const [previewFileUrl, setPreviewFileUrl] = useState<string | null>(null);

  const createFolderForm = useForm<z.infer<typeof createFolderFormSchema>>({
    resolver: zodResolver(createFolderFormSchema),
    defaultValues: {
      name: "",
    },
  });
  const previewFile = useRef<KnowledgeFile | null>(null);
  const router = useRouter();
  const onClickFile = useThrottleFn(
    (file: KnowledgeFile) => {
      if (file.is_dir) {
        enterFolder(file, currentWorkspace?.spaceCode);
      } else {
        if (file.extension === "rageval") {
          router.push(`/rageval-preview?fileId=${file.id}`);
          return;
        }
        setPreviewModalOpen(true);
        previewFile.current = file;
        getFilePreviewUrl(file.id).then((res) => {
          if (res) {
            setPreviewFileUrl(res.url);
          }
        });
      }
    },
    {
      wait: 100,
      leading: true,
    },
  );

  const onClickUpload = () => {
    const input = document.createElement("input");
    input.type = "file";
    input.onchange = async () => {
      const file = input.files?.[0];
      if (file) {
        const res = await uploadFile(file, currentDir.id);
        if (res) {
          toast.success("上传成功");
        }
      }
    };
    input.click();
  };
  const handleRename = useCallback(
    async (file: KnowledgeFile, filename: string) => {
      const success = await renameFile(file, filename, currentDir.id);
      if (success) {
        toast.success("重命名成功");
      }
      return success;
    },
    [renameFile, currentDir.id],
  );

  const handleDelete = useCallback(
    async (file: KnowledgeFile) => {
      const success = await deleteFile(file, currentDir.id);
      if (success) {
        toast.success("删除成功");
      }
      return success;
    },
    [deleteFile, currentDir.id],
  );

  const handleReUpload = useCallback(
    async (file: KnowledgeFile, newFile: File) => {
      const success = await reUploadFile(file.id, newFile, currentDir.id);
      if (success) {
        toast.success("文件更新成功");
      }
      return success;
    },
    [reUploadFile, currentDir.id],
  );

  const columns = useMemo(() => {
    return getColumns({
      onRename: handleRename,
      onDelete: handleDelete,
      onReUpload: handleReUpload,
      siblingFiles: files[currentDir.id] || [],
    });
  }, [handleRename, handleDelete, handleReUpload, files, currentDir.id]);
  const handleCreateFolder = async (
    values: z.infer<typeof createFolderFormSchema>,
  ) => {
    const res = await createFolder(values, currentWorkspace?.spaceCode);
    if (res) {
      setOpen(false);
      createFolderForm.reset();
    }
  };
  const { currentWorkspace } = useUserStore();
  useEffect(() => {
    if (currentWorkspace) {
      initPage(currentWorkspace.spaceCode);
    }
  }, [currentWorkspace, initPage]);
  return (
    <>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="flex items-center">
            <Button
              variant="ghost"
              size="icon"
              disabled={currentDirStack.length <= 1}
              onClick={() => backFolder()}
            >
              <ArrowLeftIcon />
            </Button>
          </div>
          <Breadcrumb>
            <BreadcrumbList>
              {currentDirStack.map((dir, index) => (
                <div className="flex items-center" key={dir.id}>
                  <BreadcrumbItem>
                    <BreadcrumbLink
                      className="cursor-pointer"
                      onClick={() => {
                        jumpFolder(dir.id, currentWorkspace?.spaceCode);
                      }}
                    >
                      {dir.name}
                    </BreadcrumbLink>
                  </BreadcrumbItem>
                  {index !== currentDirStack.length - 1 && (
                    <BreadcrumbSeparator />
                  )}
                </div>
              ))}
            </BreadcrumbList>
          </Breadcrumb>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="default" onClick={onClickUpload}>
            上传
          </Button>
          <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
              <Button variant="outline">新建文件夹</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>新建文件夹</DialogTitle>
              </DialogHeader>
              <Form {...createFolderForm}>
                <form
                  className="flex flex-col gap-4"
                  onSubmit={createFolderForm.handleSubmit(handleCreateFolder)}
                >
                  <FormField
                    control={createFolderForm.control}
                    name="name"
                    render={({ field }) => {
                      return (
                        <FormItem>
                          <FormLabel>文件夹名称</FormLabel>
                          <FormControl>
                            <Input placeholder="文件夹名称" {...field} />
                          </FormControl>
                        </FormItem>
                      );
                    }}
                  />
                  <DialogFooter>
                    <DialogClose asChild>
                      <Button variant="outline">取消</Button>
                    </DialogClose>
                    <Button type="submit">确认</Button>
                  </DialogFooter>
                </form>
              </Form>
            </DialogContent>
          </Dialog>
        </div>
      </div>
      <div className="flex flex-col gap-2 overflow-hidden relative">
        <DataTable
          columns={columns}
          data={files[currentDir.id]}
          tableLoading={tableLoading}
          onClickRow={(file) => onClickFile.run(file)}
        />
      </div>
      {previewModalOpen && (
        <FileViewer
          extension={previewFile?.current?.extension || ""}
          mimeType={previewFile?.current?.mime_type || ""}
          url={previewFileUrl || ""}
          onCancel={() => {
            setPreviewModalOpen(false);
            setPreviewFileUrl(null);
          }}
        />
      )}
    </>
  );
};

export default Page;
