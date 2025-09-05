import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest, NextResponse } from "next/server";

export async function POST(req: NextRequest) {
  const body = await req.json();
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/mkdir`,
    method: "POST",
    body,
  });
  const data = await res.json();
  if (data.error) {
    return NextResponse.json({
      code: data.code,
      message: data.message,
    });
  }
  if (data.code === 401) {
    return NextResponse.json(data);
  }
  return NextResponse.json({
    code: 200,
    data,
  });
}
