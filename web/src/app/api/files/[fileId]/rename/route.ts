import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest, NextResponse } from "next/server";

export async function POST(
  req: NextRequest,
  { params }: { params: { fileId: string } },
) {
  const filename = req.nextUrl.searchParams.get("filename");
  if (!filename) {
    return NextResponse.json({
      code: 400,
      message: "filename is required",
    });
  }

  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/${params.fileId}/rename`,
    method: "POST",
    query: {
      filename,
    },
  });

  const data = await res.json();
  if (data.code === 401) {
    return NextResponse.json(data);
  }

  return NextResponse.json({
    code: 200,
    data,
  });
}
