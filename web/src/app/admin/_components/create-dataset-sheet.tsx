import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { useAdminStore } from "../model";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useState, useRef } from "react";
import { Spinner } from "@/components/ui/spinner";
import { toast } from "sonner";
import { Upload, X } from "lucide-react";
import importDatasetDemo from "@/assets/import-dataset-demo.png";
import Image from "next/image";
import { requestCreateDataset } from "@/request/dataset";

const formSchema = z.object({
  name: z
    .string()
    .min(1, "数据集名称不能为空")
    .max(50, "数据集名称不能超过50个字符"),
  type: z.string(),
  remark: z.string().max(200, "数据集描述不能超过200个字符"),
});

interface CreateDatasetFormProps {
  onSuccess: () => void;
  type: "qa" | "document";
  showDocumentUpload?: boolean;
}

export function CreateDatasetForm({
  onSuccess,
  type,
  showDocumentUpload = true,
}: CreateDatasetFormProps) {
  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: "",
      type: type,
      remark: "",
    },
  });

  const [uploadedFiles, setUploadedFiles] = useState<
    Array<{ id: string; filename: string }>
  >([]);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileUpload = async (files: FileList) => {
    const file = files[0];
    if (!file) return;

    setUploading(true);
    const formData = new FormData();
    formData.append("file", file);
    const currentWorkspace = JSON.parse(
      localStorage.getItem("current_workspace") || "{}",
    );

    try {
      const response = await fetch("/api/files", {
        method: "POST",
        body: formData,
        headers: {
          "X-BELLA-SPACE-CODE": currentWorkspace.spaceCode || "",
          "X-USER-ID": localStorage.getItem("user_id") || "",
        },
      });
      const data = await response.json();
      if (data.code === 200) {
        setUploadedFiles([{ id: data.data.id, filename: data.data.filename }]);
        toast.success("文件上传成功");
      } else {
        toast.error("文件上传失败");
      }
    } catch (error) {
      console.error("Upload failed:", error);
      toast.error("文件上传失败");
    }

    setUploading(false);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      handleFileUpload(e.target.files);
    }
  };

  const removeFile = () => {
    setUploadedFiles([]);
  };

  const onSubmit = async (data: z.infer<typeof formSchema>) => {
    try {
      setSubmitting(true);
      const res = await requestCreateDataset({
        name: data.name,
        remark: data.remark,
        type,
      });
      if (res.code === 200) {
        toast.success("数据集创建成功");
        onSuccess();
      } else {
        toast.error("创建失败");
      }
    } catch {
      toast.error("数据集创建失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8 px-4">
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>数据集名称</FormLabel>
              <FormControl>
                <Input placeholder="请输入数据集名称" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="remark"
          render={({ field }) => (
            <FormItem>
              <FormLabel>数据集描述 (可选)</FormLabel>
              <FormControl>
                <Input placeholder="请输入数据集描述" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        {/* File Upload Section */}
        {showDocumentUpload && (
          <div className="space-y-4">
            <FormLabel>数据集文档 (可选)</FormLabel>
            <>
              <div className="text-xs text-gray-500">示例</div>
              <Image
                src={importDatasetDemo}
                alt="import-dataset-demo"
                width={1000}
                height={600}
              />
            </>
            <div className="border-2 border-dashed border-gray-300 rounded-md p-6">
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls"
                className="hidden"
                onChange={handleFileChange}
                disabled={uploading}
              />

              {uploading ? (
                <div className="flex flex-col items-center py-4">
                  <Spinner size="md">上传中...</Spinner>
                </div>
              ) : (
                <div
                  className="flex flex-col items-center py-4 cursor-pointer hover:bg-gray-50 transition-colors rounded-md"
                  onClick={() => fileInputRef.current?.click()}
                >
                  <Upload className="h-8 w-8 text-gray-400 mb-2" />
                  <p className="text-sm text-gray-600">点击上传文件</p>
                  <p className="text-xs text-gray-500">
                    支持 XLSX、XLS 格式，仅支持单个文件
                  </p>
                </div>
              )}
            </div>

            {/* Uploaded File */}
            {uploadedFiles.length > 0 && (
              <div className="space-y-2">
                <p className="text-sm font-medium text-gray-700">已上传文件:</p>
                <div className="flex items-center justify-between bg-gray-50 p-2 rounded-md">
                  <span className="text-sm text-gray-700">
                    {uploadedFiles[0].filename}
                  </span>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={removeFile}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}

        <Button type="submit" disabled={uploading || submitting}>
          {uploading ? "上传中..." : submitting ? "提交中..." : "提交"}
        </Button>
      </form>
    </Form>
  );
}

const CreateDatasetSheet = ({
  open,
  onOpenChange,
  type,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  type: "qa" | "document";
}) => {
  const { getDatasetList } = useAdminStore();
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetTrigger asChild>
        <Button>创建数据集</Button>
      </SheetTrigger>
      <SheetContent className="w-[800px] overflow-y-auto pb-4">
        <SheetHeader>
          <SheetTitle>创建数据集</SheetTitle>
        </SheetHeader>
        <CreateDatasetForm
          onSuccess={() => {
            getDatasetList(1, 10, type);
            onOpenChange(false);
          }}
          type={type}
          showDocumentUpload={type === "qa"}
        />
      </SheetContent>
    </Sheet>
  );
};

export default CreateDatasetSheet;
