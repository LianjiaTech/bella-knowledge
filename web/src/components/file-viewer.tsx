import React, { useEffect, useMemo, useState } from "react";
import PdfViewer from "./pdf-viewer";
import { createPortal } from "react-dom";
import { Button } from "./ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "./ui/tooltip";
import { X, ZoomIn, ZoomOutIcon } from "lucide-react";
import * as ExcelJS from "exceljs";
import { Spinner } from "./ui/spinner";
import { ScrollArea } from "./ui/scroll-area";

interface ImageViewerProps {
  url: string;
  onCancel: () => void;
}

const ImageViewer = ({ url, onCancel }: ImageViewerProps) => {
  const [scale, setScale] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const zoomIn = () => setScale((prev) => Math.min(prev * 1.2, 15));
  const zoomOut = () => setScale((prev) => Math.max(prev / 1.2, 0.2));

  useEffect(() => {
    const img = new Image();
    img.onload = () => setIsLoading(false);
    img.onerror = () => setIsLoading(false);
    img.src = url;
  }, [url]);

  return createPortal(
    <div
      className={`fixed inset-0 flex items-center justify-center bg-black/80 z-[10]`}
      onClick={(e) => e.stopPropagation()}
      tabIndex={-1}
    >
      <div
        className="h-[95vh] w-[100vw] max-w-full max-h-full overflow-auto relative flex items-center justify-center"
        style={{
          transform: `scale(${scale})`,
          transformOrigin: "center",
        }}
      >
        {isLoading ? (
          <div className="flex justify-center items-center h-full">
            <Spinner className="h-full" />
          </div>
        ) : (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={url} alt="image" className="max-w-full max-h-full" />
        )}
      </div>
      <Tooltip>
        <Button
          className="absolute top-6 right-26"
          variant="ghost"
          size="icon"
          onClick={zoomOut}
        >
          <ZoomOutIcon className="w-4 h-4 text-gray-500" />
        </Button>
      </Tooltip>
      <Tooltip>
        <Button
          className="absolute top-6 right-16"
          variant="ghost"
          size="icon"
          onClick={zoomIn}
        >
          <ZoomIn className="w-4 h-4 text-gray-500" />
        </Button>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            className="absolute top-6 right-6"
            variant="ghost"
            size="icon"
            onClick={onCancel}
          >
            <X className="w-4 h-4 text-gray-500" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>关闭</TooltipContent>
      </Tooltip>
    </div>,
    document.body,
  );
};

interface TextViewerProps {
  url: string;
  onCancel: () => void;
}

const TextViewer = ({ url, onCancel }: TextViewerProps) => {
  const [content, setContent] = useState<string>("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchTextContent = async () => {
      if (!url) {
        return;
      }
      try {
        setIsLoading(true);
        setError(null);
        const response = await fetch(url);
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const text = await response.text();
        setContent(text);
      } catch (err) {
        setError(err instanceof Error ? err.message : "加载文件失败");
      } finally {
        setIsLoading(false);
      }
    };

    fetchTextContent();
  }, [url]);
  const onClickCancel = () => {
    setContent("");
    onCancel();
  };
  return createPortal(
    <div
      className="fixed inset-0 flex items-center justify-center bg-black/80 z-[10]"
      onClick={(e) => e.stopPropagation()}
      tabIndex={-1}
    >
      <div className="bg-white rounded-lg shadow-lg w-[90vw] h-[90vh] max-w-4xl flex flex-col">
        {/* 头部工具栏 */}
        <div className="flex items-center justify-between p-4 border-b">
          <h3 className="text-lg font-semibold">文本预览</h3>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" onClick={onClickCancel}>
                <X className="w-4 h-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>关闭</TooltipContent>
          </Tooltip>
        </div>

        {/* 内容区域 */}
        <div className="flex-1 p-4 overflow-hidden">
          {isLoading ? (
            <div className="flex justify-center items-center h-full">
              <Spinner className="h-full" />
            </div>
          ) : error ? (
            <div className="flex justify-center items-center h-full">
              <p className="text-red-500">加载失败: {error}</p>
            </div>
          ) : (
            <ScrollArea className="h-full">
              <pre className="whitespace-pre-wrap break-all font-mono text-sm leading-relaxed">
                {content}
              </pre>
            </ScrollArea>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
};

interface ExcelViewerProps {
  extension: string;
  url: string;
  onCancel: () => void;
}

function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let current: string[] = [];
  let field = "";
  let inQuotes = false;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    const next = text[i + 1];

    if (inQuotes) {
      if (char === '"' && next === '"') {
        field += '"';
        i++;
      } else if (char === '"') {
        inQuotes = false;
      } else {
        field += char;
      }
    } else {
      if (char === '"') {
        inQuotes = true;
      } else if (char === ",") {
        current.push(field);
        field = "";
      } else if (char === "\n") {
        current.push(field);
        rows.push(current);
        current = [];
        field = "";
      } else if (char === "\r") {
        // ignore CR; handle CRLF as LF
      } else {
        field += char;
      }
    }
  }
  // flush last
  if (field.length > 0 || current.length > 0) {
    current.push(field);
    rows.push(current);
  }
  return rows;
}

async function parseExcel(url: string): Promise<string[][]> {
  try {
    const response = await fetch(url);
    const arrayBuffer = await response.arrayBuffer();

    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(arrayBuffer);

    // 获取第一个工作表
    const worksheet = workbook.worksheets[0];
    if (!worksheet) {
      return [];
    }

    const rows: string[][] = [];
    const maxCellCount = worksheet.columnCount;
    worksheet.eachRow((row) => {
      const rowData: string[] = Array.from({ length: maxCellCount }, () => "");
      row.eachCell((cell, colNumber) => {
        // 处理不同类型的单元格值
        let cellValue = "";
        if (cell.value !== null && cell.value !== undefined) {
          if (typeof cell.value === "object" && "text" in cell.value) {
            // 富文本
            cellValue = cell.value.text;
          } else if (typeof cell.value === "object" && "result" in cell.value) {
            // 公式
            cellValue = String(cell.value.result || cell.value.formula || "");
          } else {
            cellValue = String(cell.value);
          }
        }
        rowData[colNumber - 1] = cellValue;
      });
      rows.push(rowData);
    });
    return rows;
  } catch (error) {
    console.error("Failed to parse Excel file:", error);
    return [];
  }
}

const ExcelViewer = ({ extension, url, onCancel }: ExcelViewerProps) => {
  const [scale, setScale] = useState(1);
  const [loading, setLoading] = useState(true);
  const [rows, setRows] = useState<string[][]>([]);
  const [error, setError] = useState<string | null>(null);
  const isCsv = useMemo(() => extension.toLowerCase() === "csv", [extension]);

  const zoomIn = () => setScale((prev) => Math.min(prev * 1.2, 4));
  const zoomOut = () => setScale((prev) => Math.max(prev / 1.2, 0.5));

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      try {
        setLoading(true);
        setError(null);

        if (isCsv) {
          const res = await fetch(url);
          const text = await res.text();
          if (!cancelled) {
            setRows(parseCsv(text));
          }
        } else {
          // 使用 exceljs 解析 xlsx/xls 文件
          const parsedRows = await parseExcel(url);
          if (!cancelled) {
            setRows(parsedRows);
          }
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            `解析文件失败: ${err instanceof Error ? err.message : "未知错误"}`,
          );
          setRows([]);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    run();
    return () => {
      cancelled = true;
    };
  }, [isCsv, url]);

  return createPortal(
    <div
      className={`fixed inset-0 flex items-center justify-center bg-black/80 z-[10]`}
      onClick={(e) => e.stopPropagation()}
      tabIndex={-1}
    >
      <div
        className="h-[95vh] w-[100vw] max-w-full max-h-full overflow-auto relative bg-white"
        style={{ transform: `scale(${scale})`, transformOrigin: "center" }}
      >
        {loading ? (
          <div className="flex justify-center items-center h-full">
            <Spinner className="h-full" />
          </div>
        ) : error ? (
          <div className="flex justify-center items-center h-64 text-red-500">
            <div className="text-center">
              <p className="text-lg font-semibold mb-2">文件解析失败</p>
              <p className="text-sm">{error}</p>
            </div>
          </div>
        ) : rows.length === 0 ? (
          <div className="flex justify-center items-center h-64 text-gray-500">
            <p>文件为空或无数据</p>
          </div>
        ) : (
          <div className="w-full overflow-auto p-4">
            <table className="min-w-full divide-y divide-gray-200">
              <tbody>
                {rows.map((r, i) => (
                  <tr key={i} className="divide-x divide-gray-200">
                    {r.map((cell, j) => (
                      <td
                        key={j}
                        className={`px-3 py-2 text-sm border border-gray-200 ${
                          i === 0
                            ? "bg-gray-50 font-semibold text-gray-900"
                            : "text-gray-700"
                        }`}
                      >
                        {cell || ""}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <Tooltip>
        <Button
          className="absolute top-6 right-26"
          variant="ghost"
          size="icon"
          onClick={zoomOut}
        >
          <ZoomOutIcon className="w-4 h-4 text-gray-500" />
        </Button>
      </Tooltip>
      <Tooltip>
        <Button
          className="absolute top-6 right-16"
          variant="ghost"
          size="icon"
          onClick={zoomIn}
        >
          <ZoomIn className="w-4 h-4 text-gray-500" />
        </Button>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            className="absolute top-6 right-6"
            variant="ghost"
            size="icon"
            onClick={onCancel}
          >
            <X className="w-4 h-4 text-gray-500" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>关闭</TooltipContent>
      </Tooltip>
    </div>,
    document.body,
  );
};

interface FileViewerProps {
  extension: string;
  mimeType: string;
  url: string;
  onCancel: () => void;
}
const FileViewer = ({
  extension,
  mimeType,
  url,
  onCancel,
}: FileViewerProps) => {
  const isImage = mimeType.startsWith("image/");
  if (extension === "pdf" || extension === "docx" || extension === "doc") {
    return <PdfViewer url={url} onCancel={onCancel}></PdfViewer>;
  }
  if (isImage) {
    return <ImageViewer url={url} onCancel={onCancel}></ImageViewer>;
  }
  if (extension === "xlsx" || extension === "xls" || extension === "csv") {
    return (
      <ExcelViewer
        extension={extension}
        url={url}
        onCancel={onCancel}
      ></ExcelViewer>
    );
  }
  return <TextViewer url={url} onCancel={onCancel}></TextViewer>;
};

export default FileViewer;
