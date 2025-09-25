import { backendRequest, backendRequestFormData } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest, NextResponse } from "next/server";

export async function DELETE(
  req: NextRequest,
  { params }: { params: { fileId: string } }
) {
  const { fileId } = params;

  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/${fileId}`,
    method: "DELETE",
  });

  const data = await res.json();
  return NextResponse.json({
    code: 200,
    data: data,
  });
}

export async function PUT(
  req: NextRequest,
  { params }: { params: { fileId: string } }
) {
  const { fileId } = params;
  const reqFormData = await req.formData();
  
  const file = reqFormData.get("file");
  if (!file) {
    return NextResponse.json({
      code: 400,
      message: "文件参数缺失",
    });
  }

  const res = await backendRequestFormData(req, {
    url: `${FILE_API_URL}/v1/files?file_id=${fileId}`,
    method: "PUT",
    data: {
      file: file,
    },
  });

  try {
    const data = await res.json();
    return NextResponse.json(data);
  } catch (error) {
    return NextResponse.json({
      code: 500,
      message: "响应解析错误",
    });
  }
}