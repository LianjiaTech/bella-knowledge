import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ fileId: string }> },
) {
  const { fileId } = await params;
  return backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/${fileId}/progresses`,
    method: "GET",
  });
}
