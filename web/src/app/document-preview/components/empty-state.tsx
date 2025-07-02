"use client";

export function EmptyState() {
  return (
    <div className="flex-1 flex items-center justify-center">
      <div className="text-center text-gray-500">
        <div className="text-lg mb-2">请从左侧选择一个问题开始标注</div>
        <div className="text-sm">或者在左侧添加新问题</div>
      </div>
    </div>
  );
}
