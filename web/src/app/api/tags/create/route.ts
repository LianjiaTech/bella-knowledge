import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest } from "next/server";

export async function POST(req: NextRequest) {
  const body = await req.json();
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/tags/create`,
    method: "POST",
    body,
  });
  return res;
}