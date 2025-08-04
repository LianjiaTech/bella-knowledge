import React from "react";
interface DragWidthBarProps {
  containerRef: React.RefObject<HTMLDivElement | null>;
  minWidthPercentage: number;
  maxWidthPercentage: number;
  localStorageKey: string;
  width: number;
  setWidth: (width: number) => void;
}

const DragWidthBar = ({
  containerRef,
  minWidthPercentage,
  maxWidthPercentage,
  localStorageKey,
  width,
  setWidth,
}: DragWidthBarProps) => {
  const saveToLocalStorage = (newWidth: number) => {
    if (typeof window !== "undefined") {
      localStorage.setItem(localStorageKey, newWidth.toString());
      setWidth(newWidth);
    }
  };
  const handleMouseDown = (e: React.MouseEvent) => {
    e.preventDefault();
    const startX = e.clientX;
    const startWidth = width;
    const handleMouseMove = (e: MouseEvent) => {
      const deltaX = e.clientX - startX;
      const containerWidth = containerRef.current?.clientWidth || 0;
      const deltaPercentage = (deltaX / containerWidth) * 100;
      const newWidth = Math.max(
        minWidthPercentage,
        Math.min(maxWidthPercentage, startWidth + deltaPercentage),
      );
      saveToLocalStorage(newWidth);
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
  return (
    <div
      className="w-[1px] bg-gray-200 hover:bg-gray-300 cursor-ew-resize flex-shrink-0 relative group"
      onMouseDown={handleMouseDown}
    >
      <div className="absolute inset-0 -mx-1 group-hover:bg-opacity-20" />
    </div>
  );
};

export default DragWidthBar;
