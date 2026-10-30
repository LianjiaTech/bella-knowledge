import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest, NextResponse } from "next/server";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ fileId: string }> },
) {
  const { fileId } = await params;
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/${fileId}/progresses`,
    method: "GET",
  });
  const data = await res.json();
  if (data.error || data.code === 401) {
    return NextResponse.json(data);
  }
  return NextResponse.json({ code: 200, data });
}
