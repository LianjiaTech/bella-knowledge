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

export const getColumns = (): ColumnDef<KnowledgeFile>[] => [
  {
    accessorKey: "filename",
    header: "名称",
    cell: ({ row }) => {
      const data = row.original;
      const isDir = data.is_dir;
      const isImage = data.mime_type.startsWith("image/");
      const extension = data.mime_type.split("/")[1];
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

      return (
        <div className="flex items-center gap-2">
          {icon}
          <span title={row.getValue("filename")}>
            {row.getValue("filename")}
          </span>
        </div>
      );
    },
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
