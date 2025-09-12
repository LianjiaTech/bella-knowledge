import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest, NextResponse } from "next/server";

export async function GET(req: NextRequest) {
  const { ancestor_id, space_code } = Object.fromEntries(new URL(req.url).searchParams);
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/find`,
    method: "GET",
    query: {
      ancestor_id: ancestor_id || "",
      space_code: space_code || "",
    },
  });
  const data = await res.json();
  if (data.error) {
    return NextResponse.json({
      code: data.code,
      message: data.message,
    });
  }
  return NextResponse.json({
    code: 200,
    data,
  });
}
