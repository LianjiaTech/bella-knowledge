import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest, NextResponse } from "next/server";

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const file_id = searchParams.get("file_id") || "";
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/${file_id}/url`,
    method: "GET",
  });
  const data = await res.json();
  return NextResponse.json({
    code: 200,
    data,
  });
}
