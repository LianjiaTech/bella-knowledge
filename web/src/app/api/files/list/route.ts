import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest } from "next/server";

export async function POST(req: NextRequest) {
  const { file_ids } = await req.json();
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/files/list`,
    method: "POST",
    body: {
      file_ids,
    },
  });
  return res;
}
