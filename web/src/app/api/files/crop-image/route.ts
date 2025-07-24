import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest, NextResponse } from "next/server";

export async function POST(req: NextRequest) {
  const { file_id, bbox, page } = await req.json();
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/crop-image`,
    method: "POST",
    body: {
      file_id,
      bbox,
      page: page + 1,
    },
  });
  const data = await res.json();
  return NextResponse.json({
    code: 200,
    data,
  });
}
