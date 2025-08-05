import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest, NextResponse } from "next/server";

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const dataset_id = searchParams.get("dataset_id");
  const item_id = searchParams.get("item_id");
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/reference/list`,
    method: "POST",
    body: { dataset_id, item_id },
  });
  const responseData = await res.json();
  const { data } = responseData;
  const qaReferenceList = {
    item_id,
    references: data.map(
      (item: {
        file_id: string;
        path: string;
        reference_id: string;
        snippet: string;
      }) => ({
        file_id: item.file_id,
        path: item.path ? item.path.split("/").filter(Boolean) : [],
        reference_id: item.reference_id,
        snippet: item.snippet,
      }),
    ),
  };
  return NextResponse.json({
    code: 200,
    data: qaReferenceList,
  });
}
