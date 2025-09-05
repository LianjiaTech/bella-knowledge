import { backendRequest, backendRequestFormData } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest, NextResponse } from "next/server";

export async function GET(req: NextRequest) {
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files`,
    query: {
      purpose: "assistants",
    },
    method: "GET",
  });
  const data = await res.json();
  return NextResponse.json({
    code: 200,
    data,
  });
}

export async function POST(req: NextRequest) {
  const headers = req.headers;
  const uid = headers.get("X-User-Id");
  const reqFormData = await req.formData();

  const res = await backendRequestFormData(req, {
    url: `${FILE_API_URL}/v1/files`,
    method: "POST",
    data: {
      metadata: JSON.stringify({
        post_processors: ["file_indexing"],
        user: uid,
      }),
      file: reqFormData.get("file"),
      purpose: reqFormData.get("purpose") || "assistants",
      ancestor_id: reqFormData.get("ancestor_id") || "",
    },
  });

  const data = await res.json();
  if (data.code !== 200) {
    return NextResponse.json(data);
  }
  return NextResponse.json(data);
}
