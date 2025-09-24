"use client";

import {
  ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
  getPaginationRowModel,
  getFilteredRowModel,
  ColumnFiltersState,
  SortingState,
  getSortedRowModel,
} from "@tanstack/react-table";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useEffect, useState } from "react";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from "@/components/ui/select";
import { KnowledgeFile } from "@/lib/types/file";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { ScrollArea } from "@/components/ui/scroll-area";

interface DataTableProps<TValue> {
  columns: ColumnDef<KnowledgeFile, TValue>[];
  data: KnowledgeFile[];
  tableLoading: boolean;
  onClickRow: (row: KnowledgeFile) => void;
}

export function DataTable<TValue>({
  columns,
  data = [],
  tableLoading,
  onClickRow,
}: DataTableProps<TValue>) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
  const [selectOpen, setSelectOpen] = useState(false);
  const allExtensions = Array.from(
    new Set(
      data.map((row) => {
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
      }),
    ),
  );
  const table = useReactTable({
    data,
    columns,
    state: {
      pagination: {
        pageIndex: 0,
        pageSize: 10000,
      },
      columnFilters,
      sorting,
    },
    enableMultiRowSelection: false,
    onColumnFiltersChange: setColumnFilters,
    getFilteredRowModel: getFilteredRowModel(),
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    onSortingChange: setSorting,
    getSortedRowModel: getSortedRowModel(),
  });

  useEffect(() => {
    table.resetColumnFilters();
  }, [data]);

  const onClickSelectItem = (
    e: React.MouseEvent<HTMLDivElement>,
    value: string,
  ) => {
    e.preventDefault();
    table.getColumn("extension")?.setFilterValue(value);
  };

  const onDoubleClickSelectItem = () => {
    setSelectOpen(false);
  };

  return (
    <>
      <div className="flex items-center py-2 gap-2">
        <div className="flex items-center gap-2">
          <span className="flex-shrink-0 text-sm text-gray-500">文件名</span>
          <Input
            className="text-sm"
            placeholder="请输入文件名"
            value={
              (table.getColumn("filename")?.getFilterValue() as string) ?? ""
            }
            onChange={(e) => {
              table.getColumn("filename")?.setFilterValue(e.target.value);
            }}
          />
        </div>
        <div className="flex items-center gap-2">
          <span className="flex-shrink-0 text-sm text-gray-500">筛选类型</span>
          <Select
            value={
              (table.getColumn("extension")?.getFilterValue() as string) ?? ""
            }
            open={selectOpen}
            onOpenChange={setSelectOpen}
          >
            <SelectTrigger
              className="w-[160px]"
              onClick={() => setSelectOpen(true)}
            >
              <SelectValue placeholder="请选择文件类型" />
            </SelectTrigger>
            <SelectContent>
              {allExtensions.includes("文件夹") && (
                <SelectItem
                  onClick={(e) => onClickSelectItem(e, "文件夹")}
                  onPointerUp={(e) => onClickSelectItem(e, "文件夹")}
                  onDoubleClick={onDoubleClickSelectItem}
                  value="文件夹"
                >
                  文件夹
                </SelectItem>
              )}
              {allExtensions.filter((item) => item !== "文件夹").length > 0 && (
                <SelectGroup>
                  <SelectLabel>文件</SelectLabel>
                  {allExtensions
                    .filter((item) => item !== "文件夹")
                    .map((item, index) => (
                      <SelectItem
                        value={item}
                        key={index}
                        onClick={(e) => onClickSelectItem(e, item)}
                        onPointerUp={(e) => onClickSelectItem(e, item)}
                        onDoubleClick={onDoubleClickSelectItem}
                      >
                        {item}
                      </SelectItem>
                    ))}
                </SelectGroup>
              )}
            </SelectContent>
          </Select>
        </div>
        <Button
          size="sm"
          onClick={() => {
            table.resetColumnFilters();
          }}
        >
          重置
        </Button>
      </div>
      <ScrollArea className="flex overflow-hidden">
        <Table noWrapper>
          <TableHeader className="sticky top-0 bg-white">
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => {
                  return (
                    <TableHead key={header.id}>
                      {header.isPlaceholder
                        ? null
                        : flexRender(
                            header.column.columnDef.header,
                            header.getContext(),
                          )}
                    </TableHead>
                  );
                })}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {tableLoading ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="h-24 ">
                  <Spinner className="justify-center" />
                </TableCell>
              </TableRow>
            ) : table.getRowModel().rows?.length ? (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className="cursor-pointer h-12"
                  data-state={row.getIsSelected() && "selected"}
                  onClick={() => row.toggleSelected()}
                  onDoubleClick={() => onClickRow(row.original)}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className="text-base">
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="h-24 text-center"
                >
                  表格暂无数据
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </ScrollArea>
    </>
  );
}
