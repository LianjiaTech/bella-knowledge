import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest } from "next/server";

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const page = searchParams.get("page");
  const page_size = searchParams.get("page_size");

  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/page`,
    method: "POST",
    body: {
      page: page,
      page_size: page_size,
    },
  });
  return res;
}
