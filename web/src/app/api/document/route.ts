import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";

export async function POST(req: NextRequest) {
  const { dataset_id, file_ids } = await req.json();
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/documents/create`,
    method: "POST",
    body: {
      dataset_id,
      file_ids,
    },
  });
  return res;
}

export async function DELETE(req: NextRequest) {
  const { dataset_id, file_id } = await req.json();
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/documents/delete`,
    method: "POST",
    body: {
      dataset_id,
      file_id,
    },
  });
  return res;
}

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const dataset_id = searchParams.get("dataset_id") || "";
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/documents/list`,
    method: "POST",
    body: {
      dataset_id,
    },
  });
  return res;
}
