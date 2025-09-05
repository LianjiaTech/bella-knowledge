import { backendRequest } from "@/lib/request/backend";
import { BELLA_OPENAPI_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";

export async function GET(req: NextRequest) {
  const { headers } = req;
  const uid = headers.get("X-User-Id");
  const res = await backendRequest(req, {
    url: `${BELLA_OPENAPI_URL}/v1/space/role/list`,
    method: "GET",
    query: {
      memberUid: uid || "",
    },
  });
  return res;
}
