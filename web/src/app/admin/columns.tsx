"use client";

import { ColumnDef, Row } from "@tanstack/react-table";
import dayjs from "dayjs";
import { Edit, Import, Trash2 } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { webRequest } from "@/lib/request/web";
import { useRouter } from "next/navigation";
import {
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { AlertDialog } from "@/components/ui/alert-dialog";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { AppendDatasetSheet } from "./append-dataset-sheet";

export type Dataset = {
  id: number;
  dataset_id: string;
  space_code: string;
  name: string;
  type: string;
  remark: string;
  cuid: number;
  cu_name: string;
  ctime: string;
  muid: number;
  mu_name: string;
  mtime: string;
  status: number;
};

const CreateRowActions = (row: Row<Dataset>, onDelete: () => void) => {
  const router = useRouter();
  const [appendSheetOpen, setAppendSheetOpen] = useState(false);

  const handleEdit = () => {
    router.push(
      `document-preview?dataset_id=${row.original.dataset_id}&dataset_name=${row.original.name}`
    );
  };

  const handleAppend = () => {
    setAppendSheetOpen(true);
  };

  const handleDelete = () => {
    webRequest({
      path: `/api/dataset`,
      body: {
        dataset_id: row.original.dataset_id,
      },
      method: "DELETE",
    }).then(() => {
      onDelete();
    });
  };

  return (
    <>
      <div className="flex items-center gap-2">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              onClick={handleEdit}
              className="h-8 px-2"
            >
              <Edit className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>编辑数据集</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              onClick={handleAppend}
              className="h-8 px-2"
            >
              <Import className="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>追加数据集</TooltipContent>
        </Tooltip>
        <Tooltip>
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <div>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-8 px-2 text-red-600 hover:text-red-700"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>删除数据集</TooltipContent>
              </div>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>确定要删除这个数据集吗？</AlertDialogTitle>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>取消</AlertDialogCancel>
                <AlertDialogAction onClick={handleDelete}>
                  确定
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </Tooltip>
      </div>

      {/* 追加数据集 Sheet */}
      <AppendDatasetSheet
        open={appendSheetOpen}
        onOpenChange={setAppendSheetOpen}
        datasetName={row.original.name}
        datasetId={row.original.dataset_id}
      />
    </>
  );
};

export const getColumns = (onDelete: () => void): ColumnDef<Dataset>[] => [
  {
    accessorKey: "name",
    header: "数据集名称",
  },
  {
    accessorKey: "remark",
    header: "描述",
  },
  {
    accessorFn(row) {
      return `${row.mu_name}(${row.muid})`;
    },
    header: "更新者",
  },
  {
    accessorFn(row) {
      return dayjs(row.mtime).format("YYYY-MM-DD HH:mm:ss");
    },
    header: "更新时间",
  },
  {
    accessorFn(row) {
      return `${row.cu_name}(${row.cuid})`;
    },
    header: "创建者",
  },
  {
    accessorFn(row) {
      return dayjs(row.ctime).format("YYYY-MM-DD HH:mm:ss");
    },
    header: "创建时间",
  },
  {
    id: "actions",
    header: "操作",
    cell: ({ row }) => {
      return CreateRowActions(row, onDelete);
    },
  },
];
