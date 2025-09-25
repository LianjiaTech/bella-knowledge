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
import { useEffect, useRef, useState } from "react";

type GetColumnsOptions = {
  onRename: (file: KnowledgeFile, filename: string) => Promise<boolean>;
};

const FilenameCell = ({
  file,
  displayName,
  onRename,
}: {
  file: KnowledgeFile;
  displayName: string;
  onRename: (file: KnowledgeFile, filename: string) => Promise<boolean>;
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

  const [isHovering, setIsHovering] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [inputValue, setInputValue] = useState(file.filename);
  const [isRenaming, setIsRenaming] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

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

  return (
    <div
      className="flex items-center gap-2"
      onMouseEnter={() => setIsHovering(true)}
      onMouseLeave={() => {
        if (!isEditing) {
          setIsHovering(false);
        }
      }}
    >
      {icon}
      {isEditing ? (
        <Input
          ref={inputRef}
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onBlur={handleRename}
          onClick={(e) => e.stopPropagation()}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              handleRename();
            }
            if (e.key === "Escape") {
              e.preventDefault();
              setInputValue(file.filename);
              setIsEditing(false);
            }
          }}
          className="h-8 w-[240px]"
        />
      ) : (
        <span className="flex max-w-[240px] items-center truncate" title={displayName}>
          {displayName}
        </span>
      )}
      <div className="flex h-9 w-9 items-center justify-center">
        {(isHovering || isEditing) && (
          <Button
            size="icon"
            variant="ghost"
            onClick={(e) => {
              e.stopPropagation();
              setIsEditing(true);
              setIsHovering(true);
            }}
            disabled={isRenaming}
          >
            <Pencil size={16} />
          </Button>
        )}
      </div>
    </div>
  );
};

export const getColumns = ({ onRename }: GetColumnsOptions): ColumnDef<KnowledgeFile>[] => [
  {
    accessorKey: "filename",
    header: "名称",
    cell: ({ row }) => (
      <FilenameCell
        file={row.original}
        displayName={row.getValue("filename") as string}
        onRename={onRename}
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
