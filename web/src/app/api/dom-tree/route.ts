import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest, NextResponse } from "next/server";

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const fileId = searchParams.get("fileId");
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/${fileId}/dom-tree/url`,
    method: "GET",
  });
  return NextResponse.json({
    code: 200,
    data: await res?.json(),
  });
}
