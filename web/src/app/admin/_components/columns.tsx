"use client";

import { ColumnDef, Row } from "@tanstack/react-table";
import dayjs from "dayjs";
import { Edit, EditIcon, FilePlus2, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
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
import { requestDeleteDataset } from "@/request/dataset";
import { toast } from "sonner";
import { Input } from "@/components/ui/input";

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

const CreateRowActions = (
  row: Row<Dataset>,
  onDelete: () => void,
  type: "qa" | "document",
) => {
  const router = useRouter();
  const [appendSheetOpen, setAppendSheetOpen] = useState(false);

  const handleEdit = () => {
    if (type === "qa") {
      router.push(
        `document-preview?dataset_id=${row.original.dataset_id}&dataset_name=${row.original.name}`,
      );
    } else if (type === "document") {
      router.push(
        `document-parser?dataset_id=${row.original.dataset_id}&dataset_name=${row.original.name}`,
      );
    }
  };

  const handleAppend = () => {
    setAppendSheetOpen(true);
  };

  const handleDelete = async () => {
    const res = await requestDeleteDataset(row.original.dataset_id);
    if (res.code === 200) {
      onDelete();
    } else {
      toast.error(res.message);
    }
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
        {type === "qa" && (
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                onClick={handleAppend}
                className="h-8 px-2"
              >
                <FilePlus2 className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>追加数据集</TooltipContent>
          </Tooltip>
        )}

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

const RemarkCell = ({
  dataset,
  onChangeRemark,
}: {
  dataset: Dataset;
  onChangeRemark: (datasetId: string, remark: string) => void;
}) => {
  const [isHovering, setIsHovering] = useState(false);
  const [isInputing, setIsInputing] = useState(false);
  const [inputValue, setInputValue] = useState(dataset.remark);

  useEffect(() => {
    setInputValue(dataset.remark);
  }, [dataset.remark]);

  const handleBlur = () => {
    setIsInputing(false);
    onChangeRemark(dataset.dataset_id, inputValue);
  };

  return (
    <div
      className="min-w-6 flex items-center gap-1"
      onMouseEnter={() => setIsHovering(true)}
      onMouseLeave={() => setIsHovering(false)}
    >
      {isInputing ? (
        <Input
          autoFocus
          value={inputValue}
          onChange={(e) => {
            setInputValue(e.target.value);
          }}
          onBlur={handleBlur}
          onClick={(e) => e.stopPropagation()}
        />
      ) : dataset.remark ? (
        dataset.remark
      ) : (
        "-"
      )}
      <div className="size-9">
        {(isHovering || isInputing) && (
          <Button
            size="icon"
            variant="ghost"
            onClick={(e) => {
              e.stopPropagation();
              setIsInputing(true);
            }}
          >
            <EditIcon size={16} />
          </Button>
        )}
      </div>
    </div>
  );
};

export const getColumns = (
  onChangeRemark: (datasetId: string, remark: string) => void,
  onDelete: () => void,
  type: "qa" | "document",
): ColumnDef<Dataset>[] => [
  {
    accessorKey: "name",
    header: "数据集名称",
  },
  {
    accessorKey: "remark",
    header: "描述",
    cell: ({ row }) => (
      <RemarkCell dataset={row.original} onChangeRemark={onChangeRemark} />
    ),
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
      return CreateRowActions(row, onDelete, type);
    },
  },
];
