import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const dataset_id = searchParams.get("dataset_id") || "";
  const item_id = searchParams.get("item_id") || "";
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/reference/list`,
    method: "POST",
    body: { dataset_id, item_id },
  });
  return res;
}

export async function POST(req: NextRequest) {
  const body = await req.json();
  const { dataset_id, item_id, file_id, path, snippet, primary, children_references } =
    body;
  await Promise.all(
    children_references.map((reference_id: number) =>
      backendRequest(req, {
        url: `${FILE_API_URL}/v1/datasets/qa/reference/delete`,
        method: "POST",
        body: { dataset_id, reference_id },
      }),
    ),
  );
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/reference/create`,
    method: "POST",
    body: {
      dataset_id,
      item_id,
      file_id,
      path: path.length > 0 ? "/" + path.join("/") : "",
      snippet: snippet || "",
      primary: primary !== undefined ? primary : 0,
    },
  });
  return res;
}

export async function DELETE(req: NextRequest) {
  const body = await req.json();
  const { dataset_id, reference_id } = body;
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/reference/delete`,
    method: "POST",
    body: { dataset_id, reference_id },
  });
  return res;
}
