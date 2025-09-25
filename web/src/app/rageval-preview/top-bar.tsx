"use client";
import { ArrowLeft } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";

export default function TopBar() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const datasetName = searchParams.get("dataset_name");
  return (
    <div className="fixed top-0 left-0 right-0 z-50 w-full h-16 flex justify-between items-center px-6 shadow-md bg-white border-b">
      <div className="flex items-center gap-4">
        <ArrowLeft
          className="cursor-pointer"
          onClick={() => {
            router.push("/admin/files");
          }}
        />
        <div className="flex flex-col">
          <div className="text-lg font-bold">评测结果分析</div>
          <div className="text-xs font-semibod text-gray-500">
            RAG系统检索与生成效果评估
          </div>
        </div>

        <div className="text-sm text-gray-500">{datasetName}</div>
      </div>
    </div>
  );
}
