import { backendRequest, backendRequestFormData } from "@/lib/request/backend";
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

export async function POST(req: NextRequest) {
  try {
    const formData = await req.formData();
    const file_id = formData.get("file_id");
    const file = formData.get("file");

    // 写入临时文件
    const res = await backendRequestFormData(req, {
      url: `${FILE_API_URL}/v1/files`,
      method: "PUT",
      data: {
        file_id,
        file,
      },
    });
    const data = await res.json();
    return NextResponse.json({
      code: 200,
      data,
    });
  } catch (error) {
    console.error(error);
    return NextResponse.json({
      code: 500,
      message: "保存失败",
    });
  }
}
