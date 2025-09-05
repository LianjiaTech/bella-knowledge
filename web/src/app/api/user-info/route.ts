import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";

export async function GET(req: NextRequest) {
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/openapi/userInfo`,
    method: "GET",
  });
  return res;
}
