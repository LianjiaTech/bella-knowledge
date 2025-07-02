import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest, NextResponse } from "next/server";

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { dataset_id, item_ids } = body;

    // 输入验证
    if (!dataset_id || !Array.isArray(item_ids) || item_ids.length === 0) {
      return NextResponse.json({
        code: 400,
        message: "Invalid parameters: dataset_id and item_ids are required",
      });
    }

    // 并行处理所有请求
    const referencePromises = item_ids.map(async (item_id) => {
      try {
        const referenceRes = await backendRequest(req, {
          url: `${FILE_API_URL}/v1/datasets/qa/reference/list`,
          method: "POST",
          body: { dataset_id, item_id },
        });

        if (referenceRes && referenceRes.ok) {
          const referenceResData = await referenceRes.json();
          return referenceResData.data || [];
        }
        return [];
      } catch (error) {
        console.error(
          `Failed to fetch references for item_id ${item_id}:`,
          error
        );
        return []; // 单个请求失败不影响整体结果
      }
    });

    // 等待所有请求完成
    const referenceResults = await Promise.all(referencePromises);

    // 合并所有结果并去重文件ID
    const allReferences = referenceResults.flat();
    const uniqueFileIds = [
      ...new Set(allReferences.map((item) => item.file_id)),
    ];

    return NextResponse.json({
      code: 200,
      data: uniqueFileIds,
      total: uniqueFileIds.length,
    });
  } catch (error) {
    console.error("Error in reference-files API:", error);
    return NextResponse.json({
      code: 500,
      message: "Internal server error",
    });
  }
}
