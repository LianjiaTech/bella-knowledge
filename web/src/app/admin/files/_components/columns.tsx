"use client";

import { ColumnDef } from "@tanstack/react-table";
import { KnowledgeFile } from "@/lib/types/file";
import dayjs from "dayjs";
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  FileIcon,
  FileJsonIcon,
  FolderIcon,
  Pencil,
  MoreHorizontal,
  Trash2,
  Upload,
} from "lucide-react";
import {
  RiFileExcel2Line,
  RiFilePdf2Line,
  RiFilePpt2Line,
  RiFileWord2Line,
  RiImage2Line,
  RiMarkdownLine,
} from "@remixicon/react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useEffect, useRef, useState, useMemo } from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

type GetColumnsOptions = {
  onRename: (file: KnowledgeFile, filename: string) => Promise<boolean>;
  onDelete: (file: KnowledgeFile) => Promise<boolean>;
  onReUpload: (file: KnowledgeFile, newFile: File) => Promise<boolean>;
  siblingFiles?: KnowledgeFile[];
};

const FilenameCell = ({
  file,
  displayName,
  onRename,
  onDelete,
  onReUpload,
  siblingFiles,
}: {
  file: KnowledgeFile;
  displayName: string;
  onRename: (file: KnowledgeFile, filename: string) => Promise<boolean>;
  onDelete: (file: KnowledgeFile) => Promise<boolean>;
  onReUpload: (file: KnowledgeFile, newFile: File) => Promise<boolean>;
  siblingFiles?: KnowledgeFile[];
}) => {
  const isDir = file.is_dir;
  const isImage = file.mime_type.startsWith("image/");
  const extension = file.mime_type.split("/")[1];
  const props = {
    size: 24,
  };
  let icon = <FileIcon {...props} />;
  if (isDir) {
    icon = <FolderIcon {...props} />;
  }
  if (extension === "pdf") {
    icon = <RiFilePdf2Line {...props} />;
  }

  if (extension === "json") {
    icon = <FileJsonIcon {...props} />;
  }

  if (extension === "doc" || extension === "docx") {
    icon = <RiFileWord2Line {...props} />;
  }

  if (extension === "xls" || extension === "xlsx" || extension === "csv") {
    icon = <RiFileExcel2Line {...props} />;
  }

  if (isImage) {
    icon = <RiImage2Line {...props} />;
  }

  if (extension === "md") {
    icon = <RiMarkdownLine {...props} />;
  }

  if (extension === "ppt" || extension === "pptx") {
    icon = <RiFilePpt2Line {...props} />;
  }

  const [isEditing, setIsEditing] = useState(false);
  const [inputValue, setInputValue] = useState(file.filename);
  const [isRenaming, setIsRenaming] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 检测是否存在同名文件
  const hasNameConflict = useMemo(() => {
    if (!isEditing || !inputValue.trim() || inputValue === file.filename) {
      return false;
    }
    return siblingFiles?.some(
      (sibling) => 
        sibling.id !== file.id && 
        sibling.filename.toLowerCase() === inputValue.trim().toLowerCase()
    ) || false;
  }, [isEditing, inputValue, file.filename, file.id, siblingFiles]);

  useEffect(() => {
    setInputValue(file.filename);
  }, [file.filename]);

  useEffect(() => {
    if (isEditing) {
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [isEditing]);

  const handleRename = async () => {
    if (isRenaming) {
      return;
    }
    const trimmedValue = inputValue.trim();
    if (!trimmedValue) {
      setInputValue(file.filename);
      setIsEditing(false);
      return;
    }
    if (trimmedValue === file.filename) {
      setIsEditing(false);
      return;
    }
    
    // 前端校验：检测同名冲突
    if (hasNameConflict) {
      return; // 不允许提交，保持编辑状态
    }
    
    setIsRenaming(true);
    const success = await onRename(file, trimmedValue);
    setIsRenaming(false);
    if (success) {
      setIsEditing(false);
    } else {
      setInputValue(file.filename);
      requestAnimationFrame(() => {
        inputRef.current?.focus();
        inputRef.current?.select();
      });
    }
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    const success = await onDelete(file);
    setIsDeleting(false);
    if (success) {
      setShowDeleteDialog(false);
    }
  };

  const handleReUpload = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      setIsUploading(true);
      await onReUpload(file, selectedFile);
      setIsUploading(false);
      // Reset file input
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  return (
    <>
      <div className="group flex items-center gap-2 min-w-0 flex-1">
        {icon}
        {isEditing ? (
          <div className="flex flex-col flex-1 min-w-0">
            <Input
              ref={inputRef}
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onBlur={handleRename}
              onClick={(e) => e.stopPropagation()}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  if (!hasNameConflict) {
                    handleRename();
                  }
                }
                if (e.key === "Escape") {
                  e.preventDefault();
                  setInputValue(file.filename);
                  setIsEditing(false);
                }
              }}
              className={`h-8 ${
                hasNameConflict 
                  ? "border-red-500 focus:border-red-500 focus:ring-red-500" 
                  : ""
              }`}
            />
            {hasNameConflict && (
              <span className="text-xs text-red-500 mt-1">
                文件名已存在
              </span>
            )}
          </div>
        ) : (
          <div className="flex items-center min-w-0 flex-1">
            <span 
              className="truncate mr-2 cursor-pointer hover:text-blue-600" 
              title={displayName}
              onClick={(e) => {
                e.stopPropagation();
                setIsEditing(true);
              }}
            >
              {displayName}
            </span>
          </div>
        )}
        
        {/* 操作按钮区域 - 始终占位，透明度控制显示 */}
        <div className="flex items-center gap-1 flex-shrink-0">
          <Button
            size="icon"
            variant="ghost"
            onClick={(e) => {
              e.stopPropagation();
              setIsEditing(true);
            }}
            disabled={isRenaming}
            className={`h-8 w-8 transition-opacity duration-200 ${
              isEditing ? "opacity-100" : "opacity-0 group-hover:opacity-100"
            }`}
          >
            <Pencil size={14} />
          </Button>
          
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                size="icon"
                variant="ghost"
                onClick={(e) => e.stopPropagation()}
                className={`h-8 w-8 transition-opacity duration-200 ${
                  isEditing ? "opacity-100" : "opacity-0 group-hover:opacity-100"
                }`}
              >
                <MoreHorizontal size={14} />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent 
              align="end" 
              className="w-48"
            >
              {!isDir && (
                <DropdownMenuItem
                  onClick={(e) => {
                    e.stopPropagation();
                    handleReUpload();
                  }}
                  disabled={isUploading}
                >
                  <Upload className="mr-2 h-4 w-4" />
                  {isUploading ? "上传中..." : "重新上传"}
                </DropdownMenuItem>
              )}
              <DropdownMenuItem
                onClick={(e) => {
                  e.stopPropagation();
                  setShowDeleteDialog(true);
                }}
                className="text-red-600 focus:text-red-600"
              >
                <Trash2 className="mr-2 h-4 w-4" />
                删除
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
        
        {!isDir && (
          <input
            ref={fileInputRef}
            type="file"
            onChange={handleFileChange}
            style={{ display: 'none' }}
          />
        )}
      </div>

      <AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除 &ldquo;{file.filename}&rdquo; 吗？此操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setShowDeleteDialog(false)}>
              取消
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              disabled={isDeleting}
              className="bg-red-600 hover:bg-red-700"
            >
              {isDeleting ? "删除中..." : "删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
};

export const getColumns = ({ onRename, onDelete, onReUpload, siblingFiles }: GetColumnsOptions): ColumnDef<KnowledgeFile>[] => [
  {
    accessorKey: "filename",
    header: "名称",
    cell: ({ row }) => (
      <FilenameCell
        file={row.original}
        displayName={row.getValue("filename") as string}
        onRename={onRename}
        onDelete={onDelete}
        onReUpload={onReUpload}
        siblingFiles={siblingFiles}
      />
    ),
  },
  {
    accessorKey: "extension",
    header: "类型",
    accessorFn: (row) => {
      const isDir = row.is_dir;
      if (isDir) {
        return "文件夹";
      }
      const extension = row.extension;
      if (extension) {
        return extension;
      }
      const mimeType = row.mime_type;
      if (mimeType) {
        return mimeType.split("/")[1];
      }
      return "未知";
    },
  },
  {
    accessorKey: "created_at",
    header: ({ column }) => {
      return (
        <div className="flex items-center">
          <span>创建时间</span>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => {
              console.log(column.getIsSorted());
              if (column.getIsSorted() === "asc") {
                column.toggleSorting(true);
              } else if (column.getIsSorted() === "desc") {
                column.clearSorting();
              } else {
                column.toggleSorting(false);
              }
            }}
          >
            {!column.getIsSorted() ? (
              <ArrowUpDown />
            ) : column.getIsSorted() === "asc" ? (
              <ArrowUp />
            ) : (
              <ArrowDown />
            )}
          </Button>
        </div>
      );
    },
    cell: ({ row }) => {
      const now = Date.now();
      const createdAt = row.original.created_at;
      const diff = (now - createdAt) / 1000;
      // 一分钟内
      if (diff < 60) {
        return "刚刚";
      }
      // 一小时内
      if (diff < 60 * 60) {
        return Math.floor(diff / 60) + "分钟前";
      }
      // 一天内
      if (diff < 60 * 60 * 24) {
        return "今天";
      }
      // 两天内
      if (diff < 60 * 60 * 24 * 2) {
        return "昨天";
      }
      // 一周内
      if (diff < 60 * 60 * 24 * 7) {
        return "本周";
      }
      return dayjs(createdAt).format("YYYY-MM-DD");
    },
  },
  {
    accessorKey: "bytes",
    header: "大小",
    cell: ({ row }) => {
      const bytes = row.getValue<number>("bytes");
      const isDir = row.original.is_dir;
      if (isDir) {
        return "";
      }
      if (bytes) {
        return (bytes / 1024 / 1024).toFixed(2) + "MB";
      }
      return "0MB";
    },
  },
];
