import type { FC } from "react";
import { createPortal } from "react-dom";
import React, { useState } from "react";
import { X, ZoomIn, ZoomOutIcon } from "lucide-react";
import "react-pdf-highlighter/dist/style.css";
import { PdfHighlighter, PdfLoader } from "react-pdf-highlighter";
import { Tooltip, TooltipContent, TooltipTrigger } from "./ui/tooltip";
import { Button } from "./ui/button";
import { Spinner } from "./ui/spinner";
type PdfViewerProps = {
  url: string;
  onCancel: () => void;
};

const PdfViewer: FC<PdfViewerProps> = ({ url, onCancel }) => {
  const [scale, setScale] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });

  const zoomIn = () => {
    setScale((prevScale) => Math.min(prevScale * 1.2, 15));
    setPosition({ x: position.x - 50, y: position.y - 50 });
  };

  const zoomOut = () => {
    setScale((prevScale) => {
      const newScale = Math.max(prevScale / 1.2, 0.5);
      if (newScale === 1) setPosition({ x: 0, y: 0 });
      else setPosition({ x: position.x + 50, y: position.y + 50 });

      return newScale;
    });
  };

  return createPortal(
    <div
      className={`fixed inset-0 flex items-center justify-center bg-black/80 z-[10]`}
      onClick={(e) => e.stopPropagation()}
      tabIndex={-1}
    >
      <div
        className="h-[95vh] w-[100vw] max-w-full max-h-full overflow-hidden relative"
        style={{
          transform: `scale(${scale})`,
          transformOrigin: "center",
          scrollbarWidth: "none",
          msOverflowStyle: "none",
        }}
      >
        <PdfLoader
          workerSrc="/pdf.worker.min.mjs"
          url={url}
          beforeLoad={
            <div className="flex justify-center items-center h-full">
              <Spinner className="h-full" />
            </div>
          }
        >
          {(pdfDocument) => {
            return (
              <PdfHighlighter
                pdfDocument={pdfDocument}
                enableAreaSelection={(event) => event.altKey}
                scrollRef={() => {}}
                onScrollChange={() => {}}
                onSelectionFinished={() => null}
                highlightTransform={() => {
                  return <div />;
                }}
                highlights={[]}
              />
            );
          }}
        </PdfLoader>
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

export default PdfViewer;
