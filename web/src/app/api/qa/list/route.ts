import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest } from "next/server";

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const dataset_id = searchParams.get("dataset_id") || "";
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/list`,
    method: "POST",
    body: { dataset_id },
  });
  return res;
}
