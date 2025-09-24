"use client";

import React, { useState, useEffect, useRef, useCallback } from "react";
import { Document, Page, pdfjs } from "react-pdf";
import {
  ChevronLeft,
  ChevronRight,
  ZoomIn,
  ZoomOut,
  FileText,
  Edit3,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useDocumentParserStore } from "../model";
import { toast } from "sonner";
import { getFilePreviewUrl } from "@/request/files";

// 设置PDF.js worker
pdfjs.GlobalWorkerOptions.workerSrc = `//cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjs.version}/pdf.worker.min.mjs`;

type BboxPosition = {
  bbox: [number, number, number, number];
  page: number;
  id: string;
  type: "auto" | "manual";
};

interface DocumentContentProps {
  documentContent: {
    fileId: string;
    type: "pdf" | "docx" | "doc";
    name: string;
  } | null;
  currentEditingPositions?: BboxPosition[] | null;
  width: number;
}

const DocumentContent: React.FC<DocumentContentProps> = ({
  documentContent,
  currentEditingPositions,
  width,
}) => {
  const [numPages, setNumPages] = useState<number>(0);
  const [scale, setScale] = useState<number>(1.0);
  const [loading, setLoading] = useState<boolean>(true);
  const [pdfLoading, setPdfLoading] = useState<boolean>(true);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 鼠标绘制状态
  const [isDrawing, setIsDrawing] = useState<boolean>(false);
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [isResizing, setIsResizing] = useState<boolean>(false);
  const [isProcessingScreenshot, setIsProcessingScreenshot] =
    useState<boolean>(false);
  const [draggedBoxId, setDraggedBoxId] = useState<string | null>(null);
  const [resizeHandle, setResizeHandle] = useState<string | null>(null);
  const [startPoint, setStartPoint] = useState<{ x: number; y: number } | null>(
    null,
  );
  const [currentBox, setCurrentBox] = useState<BboxPosition | null>(null);
  const [dragOffset, setDragOffset] = useState<{ x: number; y: number }>({
    x: 0,
    y: 0,
  });

  // 获取store方法
  const {
    isDrawingMode,
    addManualPosition,
    updatePosition,
    setDrawingMode,
    currentPdfPage,
    setPdfPage,
    isScreenshotMode,
    completeScreenshot,
    undoLastAction,
    recordAction,
    setScreenshotMode,
    allBlockPositions,
    currentEditingBlock,
    activePositionOverlay,
    clickPositionOverlay,
    removePosition,
  } = useDocumentParserStore();

  // PDF容器引用
  const pdfContainerRef = useRef<HTMLDivElement>(null);

  // 获取预览链接
  const fetchPreviewUrl = useCallback(async (fileId: string) => {
    try {
      setLoading(true);
      setError(null);

      const data = await getFilePreviewUrl(fileId);
      if (data) {
        setPreviewUrl(data.url);
      } else {
        setError("获取预览链接失败");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "获取预览链接失败");
    } finally {
      setLoading(false);
    }
  }, []);

  // 监听文档内容变化
  useEffect(() => {
    if (documentContent) {
      fetchPreviewUrl(documentContent.fileId);
    } else {
      setPreviewUrl(null);
      setError(null);
      setLoading(false);
    }
  }, [documentContent, fetchPreviewUrl]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.ctrlKey && event.key === "z") {
        // Call the undo function from the store
        undoLastAction();
      }
      if (event.key === "Escape") {
        setScreenshotMode(false);
      }
      if (event.key === "Delete") {
        if (activePositionOverlay) {
          removePosition(activePositionOverlay.position.id);
          recordAction({
            type: "delete",
            position: activePositionOverlay.position,
            deleteIndex: activePositionOverlay.index,
          });
        }
      }
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [
    activePositionOverlay,
    recordAction,
    removePosition,
    setScreenshotMode,
    undoLastAction,
  ]);

  const onDocumentLoadSuccess = ({ numPages }: { numPages: number }) => {
    setNumPages(numPages);
    setPdfLoading(false);
  };

  const onDocumentLoadError = () => {
    setError("PDF文档加载失败");
    setPdfLoading(false);
  };

  const goToPrevPage = () => {
    setPdfPage(Math.max(currentPdfPage - 1, 1));
  };

  const goToNextPage = () => {
    setPdfPage(Math.min(currentPdfPage + 1, numPages));
  };

  const zoomIn = () => {
    setScale((prev) => Math.min(prev + 0.2, 3.0));
  };

  const zoomOut = () => {
    setScale((prev) => Math.max(prev - 0.2, 0.5));
  };

  // 获取相对页面的坐标
  const getRelativeCoordinates = useCallback(
    (event: React.MouseEvent, pageElement: HTMLElement) => {
      const rect = pageElement.getBoundingClientRect();
      const x = (event.clientX - rect.left) / scale;
      const y = (event.clientY - rect.top) / scale;
      return { x, y };
    },
    [scale],
  );

  // 检查是否点击在调整手柄上
  const getResizeHandle = useCallback(
    (
      coords: { x: number; y: number },
      bbox: [number, number, number, number],
    ): string | null => {
      const [x1, y1, x2, y2] = bbox;
      const handleSize = 7 / scale;

      // 角落手柄
      if (
        coords.x >= x1 - handleSize &&
        coords.x <= x1 + handleSize &&
        coords.y >= y1 - handleSize &&
        coords.y <= y1 + handleSize
      )
        return "nw";
      if (
        coords.x >= x2 - handleSize &&
        coords.x <= x2 + handleSize &&
        coords.y >= y1 - handleSize &&
        coords.y <= y1 + handleSize
      )
        return "ne";
      if (
        coords.x >= x1 - handleSize &&
        coords.x <= x1 + handleSize &&
        coords.y >= y2 - handleSize &&
        coords.y <= y2 + handleSize
      )
        return "sw";
      if (
        coords.x >= x2 - handleSize &&
        coords.x <= x2 + handleSize &&
        coords.y >= y2 - handleSize &&
        coords.y <= y2 + handleSize
      )
        return "se";

      // 边缘手柄
      if (
        coords.x >= x1 - handleSize &&
        coords.x <= x2 + handleSize &&
        coords.y >= y1 - handleSize &&
        coords.y <= y1 + handleSize
      )
        return "n";
      if (
        coords.x >= x1 - handleSize &&
        coords.x <= x2 + handleSize &&
        coords.y >= y2 - handleSize &&
        coords.y <= y2 + handleSize
      )
        return "s";
      if (
        coords.x >= x1 - handleSize &&
        coords.x <= x1 + handleSize &&
        coords.y >= y1 - handleSize &&
        coords.y <= y2 + handleSize
      )
        return "w";
      if (
        coords.x >= x2 - handleSize &&
        coords.x <= x2 + handleSize &&
        coords.y >= y1 - handleSize &&
        coords.y <= y2 + handleSize
      )
        return "e";

      return null;
    },
    [scale],
  );

  // 鼠标按下事件
  const handleMouseDown = useCallback(
    (event: React.MouseEvent) => {
      if (!isDrawingMode && !isScreenshotMode) return;

      const pageElement = event.currentTarget as HTMLElement;
      const coords = getRelativeCoordinates(event, pageElement);

      if (!coords) return;

      const currentPage = currentPdfPage - 1;

      // 如果是截图模式，直接开始绘制截图区域
      if (isScreenshotMode) {
        setIsDrawing(true);
        setStartPoint(coords);
        setCurrentBox({
          bbox: [coords.x, coords.y, coords.x, coords.y],
          page: currentPage,
          id: `screenshot-${Date.now()}`,
          type: "manual",
        });
        return;
      }
      // TODO: 出现多个框重叠的时候，优先选择最后的那一个，因为是一个层叠的关系
      const clickedBox = currentEditingPositions?.findLast((pos) => {
        const [x1, y1, x2, y2] = pos.bbox;
        return (
          coords.x >= x1 - 5 / scale &&
          coords.x <= x2 + 5 / scale &&
          coords.y >= y1 - 5 / scale &&
          coords.y <= y2 + 5 / scale &&
          pos.page === currentPage
        );
      });

      if (clickedBox) {
        const handle = getResizeHandle(coords, clickedBox.bbox);
        if (handle) {
          setIsResizing(true);
          setDraggedBoxId(clickedBox.id);
          setResizeHandle(handle);
          setStartPoint(coords);
          recordAction({
            type: "edit",
            position: clickedBox,
            previousBbox: clickedBox.bbox,
          });
        } else {
          setIsDragging(true);
          setDraggedBoxId(clickedBox.id);
          const [x1, y1] = clickedBox.bbox;
          setDragOffset({ x: coords.x - x1, y: coords.y - y1 });
          recordAction({
            type: "edit",
            position: clickedBox,
            previousBbox: clickedBox.bbox,
          });
        }
      } else {
        setIsDrawing(true);
        setStartPoint(coords);
        setCurrentBox({
          bbox: [coords.x, coords.y, coords.x, coords.y],
          page: currentPage,
          id: `manual-${Date.now()}`,
          type: "manual",
        });
      }
    },
    [
      isDrawingMode,
      isScreenshotMode,
      getRelativeCoordinates,
      currentPdfPage,
      currentEditingPositions,
      scale,
      getResizeHandle,
      recordAction,
    ],
  );

  // 鼠标移动事件
  const handleMouseMove = useCallback(
    (event: React.MouseEvent) => {
      if (
        (!isDrawingMode && !isScreenshotMode) ||
        (!isDrawing && !isDragging && !isResizing) ||
        isProcessingScreenshot
      )
        return;

      const pageElement = event.currentTarget as HTMLElement;
      const coords = getRelativeCoordinates(event, pageElement);

      if (!coords) return;

      if (isDrawing && startPoint && currentBox) {
        const newBox: BboxPosition = {
          ...currentBox,
          bbox: [
            Math.min(startPoint.x, coords.x),
            Math.min(startPoint.y, coords.y),
            Math.max(startPoint.x, coords.x),
            Math.max(startPoint.y, coords.y),
          ],
        };
        setCurrentBox(newBox);
      } else if (isDragging && draggedBoxId) {
        const targetBox = currentEditingPositions?.find(
          (pos) => pos.id === draggedBoxId,
        );
        if (targetBox) {
          const [, , width, height] = targetBox.bbox;
          const newX = coords.x - dragOffset.x;
          const newY = coords.y - dragOffset.y;
          const newBbox: [number, number, number, number] = [
            newX,
            newY,
            newX + (width - targetBox.bbox[0]),
            newY + (height - targetBox.bbox[1]),
          ];
          updatePosition(draggedBoxId, newBbox);
        }
      } else if (isResizing && draggedBoxId && resizeHandle) {
        const targetBox = currentEditingPositions?.find(
          (pos) => pos.id === draggedBoxId,
        );
        if (targetBox) {
          const [x1, y1, x2, y2] = targetBox.bbox;
          let newBbox: [number, number, number, number] = [x1, y1, x2, y2];

          switch (resizeHandle) {
            case "nw":
              newBbox = [coords.x, coords.y, x2, y2];
              break;
            case "ne":
              newBbox = [x1, coords.y, coords.x, y2];
              break;
            case "sw":
              newBbox = [coords.x, y1, x2, coords.y];
              break;
            case "se":
              newBbox = [x1, y1, coords.x, coords.y];
              break;
            case "n":
              newBbox = [x1, coords.y, x2, y2];
              break;
            case "s":
              newBbox = [x1, y1, x2, coords.y];
              break;
            case "w":
              newBbox = [coords.x, y1, x2, y2];
              break;
            case "e":
              newBbox = [x1, y1, coords.x, y2];
              break;
          }

          const minSize = 5;
          if (
            Math.abs(newBbox[2] - newBbox[0]) >= minSize &&
            Math.abs(newBbox[3] - newBbox[1]) >= minSize
          ) {
            updatePosition(draggedBoxId, newBbox);
          }
        }
      }
    },
    [
      isDrawingMode,
      isScreenshotMode,
      isDrawing,
      isDragging,
      isResizing,
      isProcessingScreenshot,
      startPoint,
      currentBox,
      draggedBoxId,
      dragOffset,
      resizeHandle,
      currentEditingPositions,
      updatePosition,
      getRelativeCoordinates,
    ],
  );

  // Calculate the area of the box
  const calculateBoxArea = (bbox: [number, number, number, number]) => {
    const [x1, y1, x2, y2] = bbox;
    return Math.abs(x2 - x1) * Math.abs(y2 - y1);
  };

  const handleMouseUp = useCallback(async () => {
    if (isDrawing && currentBox) {
      const area = calculateBoxArea(currentBox.bbox);
      if (area < 20) {
        // 防误触
        setIsDrawing(false);
        setStartPoint(null);
        setCurrentBox(null);
        return;
      }

      if (isScreenshotMode) {
        // 截图模式：生成截图并调用回调
        setIsProcessingScreenshot(true);
        const canvas = document.createElement("canvas");
        const ctx = canvas.getContext("2d");

        if (ctx && pdfContainerRef.current) {
          const pdfElement = pdfContainerRef.current.querySelector(
            ".react-pdf__Page__canvas",
          ) as HTMLCanvasElement;
          if (pdfElement) {
            try {
              const res = await completeScreenshot(
                currentBox.bbox,
                currentBox.page,
              );
              if (!res) {
                toast.error("截图失败，可能存在无效区域，请重新截图");
              }
            } catch (error) {
              console.error("Screenshot failed:", error);
              toast.error("截图失败");
            } finally {
              setIsProcessingScreenshot(false);
            }
          }
        }
      } else {
        addManualPosition(currentBox);
        recordAction({
          type: "add",
          position: currentBox,
        });
      }
      setIsDrawing(false);
      setStartPoint(null);
      setCurrentBox(null);
    } else if (isDragging) {
      setIsDragging(false);
      setDraggedBoxId(null);
      setDragOffset({ x: 0, y: 0 });
    } else if (isResizing) {
      setIsResizing(false);
      setDraggedBoxId(null);
      setResizeHandle(null);
      setStartPoint(null);
    }
  }, [
    isDrawing,
    currentBox,
    isDragging,
    isResizing,
    isScreenshotMode,
    completeScreenshot,
    addManualPosition,
    recordAction,
  ]);

  // 渲染调整手柄
  const renderResizeHandles = (position: BboxPosition) => {
    const [x1, y1, x2, y2] = position.bbox;
    const handleSize = 9;

    const handles = [
      { key: "nw", x: x1, y: y1, cursor: "nw-resize" },
      { key: "ne", x: x2, y: y1, cursor: "ne-resize" },
      { key: "sw", x: x1, y: y2, cursor: "sw-resize" },
      { key: "se", x: x2, y: y2, cursor: "se-resize" },
      { key: "n", x: (x1 + x2) / 2, y: y1, cursor: "n-resize" },
      { key: "s", x: (x1 + x2) / 2, y: y2, cursor: "s-resize" },
      { key: "w", x: x1, y: (y1 + y2) / 2, cursor: "w-resize" },
      { key: "e", x: x2, y: (y1 + y2) / 2, cursor: "e-resize" },
    ];

    return handles.map((handle) => (
      <div
        key={handle.key}
        className="absolute bg-white border-2 border-gray-600 pointer-events-auto"
        style={{
          left: `${handle.x * scale - handleSize / 2}px`,
          top: `${handle.y * scale - handleSize / 2}px`,
          width: `${handleSize}px`,
          height: `${handleSize}px`,
          cursor: handle.cursor,
        }}
      />
    ));
  };

  // 渲染红色框overlay
  const renderPositionOverlay = () => {
    const currentPagePositions = currentEditingBlock?.id
      ? allBlockPositions[currentEditingBlock.id]?.filter(
          (pos: BboxPosition) => pos.page === currentPdfPage - 1,
        ) || []
      : [];

    const allPositions = [...currentPagePositions];
    if (currentBox && currentBox.page === currentPdfPage - 1) {
      allPositions.push(currentBox);
    }

    if (allPositions.length === 0) return null;

    return (
      <div className="absolute inset-0">
        {allPositions.map((position, index) => {
          const [x1, y1, x2, y2] = position.bbox;
          const isManual = position.type === "manual";
          const isBeingDragged = position.id === draggedBoxId;
          const isScreenshotBox = position.id?.startsWith("screenshot-");
          const showHandles =
            isDrawingMode && activePositionOverlay?.position.id === position.id;
          return (
            <div
              key={position.id || index}
              className="absolute"
              onClick={() =>
                clickPositionOverlay({
                  index: index,
                  position: position,
                })
              }
            >
              <div
                data-overlay={index}
                className={`cursor-pointer absolute border-2 ${
                  isScreenshotBox
                    ? "border-green-500 bg-green-100"
                    : isManual
                      ? "border-blue-500 bg-blue-100"
                      : "border-red-500 bg-red-100"
                } ${isBeingDragged ? "opacity-80" : "opacity-60"}`}
                style={{
                  left: `${x1 * scale}px`,
                  top: `${y1 * scale}px`,
                  width: `${(x2 - x1) * scale}px`,
                  height: `${(y2 - y1) * scale}px`,
                  transform: "translate(0, 0)",
                }}
              />
              {showHandles && renderResizeHandles(position)}
            </div>
          );
        })}
      </div>
    );
  };

  // 加载状态渲染
  const renderLoadingState = () => (
    <div className="flex-1 flex flex-col items-center justify-center h-full">
      <div className="space-y-4 w-full max-w-2xl mx-auto p-4">
        <Skeleton className="w-full h-6" />
        <Skeleton className="w-full h-96" />
        <Skeleton className="w-full h-6" />
      </div>
    </div>
  );

  // 错误状态渲染
  const renderErrorState = () => (
    <div className="flex-1 flex flex-col items-center justify-center h-full">
      <div className="text-center space-y-4">
        <FileText className="w-16 h-16 text-red-300 mx-auto" />
        <p className="text-red-500 text-lg font-medium">文档加载失败</p>
        <p className="text-gray-500 text-sm">{error}</p>
        <Button
          variant="outline"
          onClick={() => {
            if (documentContent) {
              fetchPreviewUrl(documentContent.fileId);
            }
          }}
        >
          重新加载
        </Button>
      </div>
    </div>
  );

  // Add a semi-transparent overlay when in screenshot mode with cut-out effect
  const renderOverlay = () => {
    if (!isScreenshotMode) return null;

    // If there's a current box being drawn, create a cut-out effect
    if (currentBox && currentBox.page === currentPdfPage - 1) {
      const [x1, y1, x2, y2] = currentBox.bbox;
      const scaledX1 = x1 * scale;
      const scaledY1 = y1 * scale;
      const scaledX2 = x2 * scale;
      const scaledY2 = y2 * scale;

      return (
        <div className="absolute inset-0 z-10 pointer-events-none">
          {/* Top area */}
          <div
            className="absolute bg-black opacity-50"
            style={{
              top: 0,
              left: 0,
              right: 0,
              height: `${scaledY1}px`,
            }}
          />
          {/* Bottom area */}
          <div
            className="absolute bg-black opacity-50"
            style={{
              top: `${scaledY2}px`,
              left: 0,
              right: 0,
              bottom: 0,
            }}
          />
          {/* Left area */}
          <div
            className="absolute bg-black opacity-50"
            style={{
              top: `${scaledY1}px`,
              left: 0,
              width: `${scaledX1}px`,
              height: `${scaledY2 - scaledY1}px`,
            }}
          />
          {/* Right area */}
          <div
            className="absolute bg-black opacity-50"
            style={{
              top: `${scaledY1}px`,
              left: `${scaledX2}px`,
              right: 0,
              height: `${scaledY2 - scaledY1}px`,
            }}
          />
          {/* Highlight border for the cut-out area */}
          <div
            className="absolute border-2 border-green-400"
            style={{
              left: `${scaledX1}px`,
              top: `${scaledY1}px`,
              width: `${scaledX2 - scaledX1}px`,
              height: `${scaledY2 - scaledY1}px`,
            }}
          />
        </div>
      );
    }

    // Default full overlay when no selection
    return (
      <div
        className="absolute inset-0 bg-black opacity-50 z-10 pointer-events-none"
        style={{ cursor: "crosshair" }}
      />
    );
  };

  // PDF预览渲染
  const renderPDF = () => {
    if (loading) return renderLoadingState();
    if (error) return renderErrorState();
    if (!previewUrl) return null;

    return (
      <div className="flex-1 flex flex-col overflow-hidden relative">
        {/* PDF工具栏 */}
        <div className="flex items-center justify-between p-2 border-b gap-2">
          <div className="flex items-center gap-2 flex-shrink-0">
            <Button
              variant="outline"
              size="sm"
              onClick={goToPrevPage}
              disabled={currentPdfPage <= 1}
            >
              <ChevronLeft className="w-4 h-4" />
            </Button>
            <span className="text-sm">
              {currentPdfPage} / {numPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={goToNextPage}
              disabled={currentPdfPage >= numPages}
            >
              <ChevronRight className="w-4 h-4" />
            </Button>
          </div>

          <div className="text-base font-semibold leading-8 flex-shrink-0">
            原始文档
          </div>

          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={zoomOut}>
              <ZoomOut className="w-4 h-4" />
            </Button>
            <span className="text-sm">{Math.round(scale * 100)}%</span>
            <Button variant="outline" size="sm" onClick={zoomIn}>
              <ZoomIn className="w-4 h-4" />
            </Button>
            <Button
              variant={isDrawingMode ? "default" : "outline"}
              size="sm"
              onClick={() => setDrawingMode(!isDrawingMode)}
              title={isDrawingMode ? "退出绘制模式" : "进入绘制模式"}
              disabled={isScreenshotMode}
            >
              <Edit3 className="w-4 h-4" />
            </Button>
          </div>
        </div>

        {/* PDF内容 */}
        <div className="flex-1 bg-gray-100 overflow-auto">
          <div className="flex justify-center p-4">
            <div
              ref={pdfContainerRef}
              className="relative"
              onMouseDown={handleMouseDown}
              onMouseMove={handleMouseMove}
              onMouseUp={handleMouseUp}
              style={{
                cursor: isScreenshotMode
                  ? "crosshair"
                  : isDrawingMode
                    ? "crosshair"
                    : "default",
              }}
            >
              {renderOverlay()}
              <Document
                file={previewUrl}
                onLoadStart={() => setPdfLoading(true)}
                onLoadSuccess={onDocumentLoadSuccess}
                onLoadError={onDocumentLoadError}
                loading={<Skeleton className="w-full h-96" />}
              >
                <Page
                  pageNumber={currentPdfPage}
                  scale={scale}
                  renderTextLayer={false}
                  renderAnnotationLayer={false}
                  className="shadow-md"
                />
              </Document>
              {!pdfLoading && renderPositionOverlay()}
            </div>
          </div>
        </div>
      </div>
    );
  };

  // 空状态渲染
  const renderEmptyState = () => (
    <div className="flex-1 flex flex-col items-center justify-center h-full text-gray-500">
      <FileText className="w-16 h-16 mb-4 text-gray-300" />
      <p className="text-lg font-medium">暂无文件</p>
      <p className="text-sm">请选择一个文件进行预览</p>
    </div>
  );
  return (
    <div
      className="flex flex-col border-l border-r overflow-hidden min-w-[500px]"
      style={{ width: `${width}%` }}
    >
      <div className="flex-1 flex overflow-hidden">
        <div className="flex-1 flex overflow-hidden">
          {!documentContent && renderEmptyState()}
          {documentContent && renderPDF()}
        </div>
      </div>
    </div>
  );
};

export default DocumentContent;
