import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";

export async function PUT(req: NextRequest) {
  const body = await req.json();
  const { dataset_id, reference_id, primary } = body;
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/reference/update`,
    method: "POST",
    body: {
      dataset_id,
      reference_id,
      primary: primary !== undefined ? primary : 0,
    },
  });
  return res;
}