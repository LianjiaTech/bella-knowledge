"use client";
import React, { useEffect, useState } from "react";
import LeftSidebar from "./_components/left-sidebar";
import Parser from "./_components/parser";
import { useDocumentParserStore } from "./model";
import dynamic from "next/dynamic";

const DocumentContentClient = dynamic(
  () => import("./_components/document-content"),
  { ssr: false },
);

const DocumentParserPage = () => {
  const { documentContent, currentEditingPositions, clearModel } =
    useDocumentParserStore();

  // 从localStorage读取初始宽度，如果不存在则使用默认值50%
  const [documentContentWidth, setDocumentContentWidth] = useState(() => {
    if (typeof window !== "undefined") {
      const saved = localStorage.getItem("documentContentWidth");
      return saved ? parseFloat(saved) : 50;
    }
    return 50;
  });

  // 保存宽度到localStorage
  const saveWidthToStorage = (width: number) => {
    if (typeof window !== "undefined") {
      localStorage.setItem("documentContentWidth", width.toString());
    }
  };

  // 拖动处理逻辑
  const handleMouseDown = (e: React.MouseEvent) => {
    e.preventDefault();
    const startX = e.clientX;
    const startWidth = documentContentWidth;

    const handleMouseMove = (e: MouseEvent) => {
      const deltaX = e.clientX - startX;
      const containerWidth = window.innerWidth; // 整个窗口宽度
      const deltaPercentage = (deltaX / containerWidth) * 100;
      const newWidth = Math.max(20, Math.min(70, startWidth + deltaPercentage)); // 限制在20%-70%之间
      setDocumentContentWidth(newWidth);
      saveWidthToStorage(newWidth); // 实时保存到localStorage
    };

    const handleMouseUp = () => {
      document.removeEventListener("mousemove", handleMouseMove);
      document.removeEventListener("mouseup", handleMouseUp);
      document.body.style.cursor = "default";
      document.body.style.userSelect = "auto";
    };

    document.addEventListener("mousemove", handleMouseMove);
    document.addEventListener("mouseup", handleMouseUp);
    document.body.style.cursor = "ew-resize";
    document.body.style.userSelect = "none";
  };

  useEffect(() => {
    return () => {
      clearModel();
    };
  }, [clearModel]);
  return (
    <>
      <LeftSidebar />
      <DocumentContentClient
        documentContent={documentContent}
        currentEditingPositions={currentEditingPositions}
        width={documentContentWidth}
      />
      {/* 拖动条 */}
      <div
        className="w-[1px] bg-gray-200 hover:bg-gray-300 cursor-ew-resize flex-shrink-0 relative group"
        onMouseDown={handleMouseDown}
      >
        <div className="absolute inset-0 -mx-1 group-hover:bg-opacity-20" />
      </div>
      <Parser width={100 - documentContentWidth} />
    </>
  );
};

export default DocumentParserPage;
