"use client";

import React from "react";
import { cn } from "@/lib/utils";

interface TableCell {
  text: string;
  path: number[]; // [start_row, end_row, start_column, end_column]
}

interface TableRow {
  cells: TableCell[];
}

interface TableRendererProps {
  rows: TableRow[];
}

export function TableRenderer({ rows }: TableRendererProps) {
  if (!rows || rows.length === 0) {
    return <div className="p-8 text-center text-gray-500">暂无表格数据</div>;
  }

  // 收集所有单元格信息
  const allCells: Array<TableCell & { rowIndex: number }> = [];
  rows.forEach((row, rowIndex) => {
    row.cells.forEach((cell) => {
      allCells.push({ ...cell, rowIndex });
    });
  });

  // 计算表格的最大行数和列数
  let maxRow = 0;
  let maxCol = 0;
  allCells.forEach((cell) => {
    const [, endRow, , endCol] = cell.path;
    maxRow = Math.max(maxRow, endRow);
    maxCol = Math.max(maxCol, endCol);
  });

  // 创建表格矩阵，用于标记已占用的位置
  const matrix: Array<Array<TableCell | null>> = Array(maxRow + 1)
    .fill(null)
    .map(() => Array(maxCol + 1).fill(null));

  // 填充矩阵
  allCells.forEach((cell) => {
    const [startRow, endRow, startCol, endCol] = cell.path;
    for (let r = startRow; r <= endRow; r++) {
      for (let c = startCol; c <= endCol; c++) {
        if (r === startRow && c === startCol) {
          matrix[r][c] = cell;
        } else {
          matrix[r][c] = { ...cell, text: "" }; // 占位符
        }
      }
    }
  });

  // 渲染表格
  const renderTable = () => {
    const tableRows = [];

    for (let r = 0; r <= maxRow; r++) {
      const tableCells = [];

      for (let c = 0; c <= maxCol; c++) {
        const cell = matrix[r][c];
        if (!cell) continue;

        // 只渲染起始位置的单元格
        const [startRow, endRow, startCol, endCol] = cell.path;
        if (r === startRow && c === startCol) {
          const rowSpan = endRow - startRow + 1;
          const colSpan = endCol - startCol + 1;
          const isHeader = r === 0; // 第一行作为表头

          tableCells.push(
            <td
              key={`${r}-${c}`}
              rowSpan={rowSpan > 1 ? rowSpan : undefined}
              colSpan={colSpan > 1 ? colSpan : undefined}
              className={cn(
                "px-4 py-3 text-sm border border-gray-200",
                isHeader
                  ? "bg-gray-50 font-semibold text-gray-900"
                  : "bg-white text-gray-700 hover:bg-gray-50",
                "transition-colors duration-150"
              )}
            >
              <div className="whitespace-pre-wrap break-words">
                {cell.text || "-"}
              </div>
            </td>
          );
        }
      }

      if (tableCells.length > 0) {
        tableRows.push(
          <tr key={r} className="divide-x divide-gray-200">
            {tableCells}
          </tr>
        );
      }
    }

    return tableRows;
  };

  return (
    <div className="w-full">
      <table className="min-w-full divide-y divide-gray-200 bg-white">
        <tbody className="divide-y divide-gray-100">{renderTable()}</tbody>
      </table>
    </div>
  );
}
