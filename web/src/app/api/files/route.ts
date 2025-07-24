import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
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
  const formData = new FormData();
  formData.append("file", reqFormData.get("file") as File);
  formData.append(
    "metadata",
    JSON.stringify({
      post_processors: ["file_indexing"],
      user: uid,
    }),
  );
  const purpose = reqFormData.get("purpose");
  formData.append("purpose", purpose || "assistants");

  const currentWorkspace = req.headers.get("X-BELLA-SPACE-CODE");
  const res = await fetch(`${FILE_API_URL}/v1/files`, {
    method: "POST",
    headers: {
      "X-BELLA-CONSOLE": "true",
      "X-BELLA-SPACE-CODE": currentWorkspace || "",
      cookie: req.cookies.toString(),
    },
    body: formData,
  });

  const data = await res.json();
  return NextResponse.json({
    code: 200,
    data,
  });
}
