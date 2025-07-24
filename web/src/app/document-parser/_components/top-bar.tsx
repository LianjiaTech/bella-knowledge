"use client";
import { ArrowLeft } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";

export default function TopBar({ lastEditTime }: { lastEditTime: number }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const datasetName = searchParams.get("dataset_name");
  return (
    <div className="w-full h-16 flex justify-between items-center px-6 shadow-md bg-white border-b">
      <div className="flex items-center gap-4">
        <ArrowLeft
          className="cursor-pointer"
          onClick={() => {
            router.push("/admin?tab=document-parser");
          }}
        />
        <div className="text-lg font-bold">文档解析</div>
        <div className="text-sm text-gray-500">{datasetName}</div>
        {lastEditTime > 0 && (
          <div className="text-xs text-gray-400">
            最后一次修改时间：{new Date(lastEditTime).toLocaleString()}
          </div>
        )}
      </div>
    </div>
  );
}
