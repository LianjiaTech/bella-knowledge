import { backendRequest } from "@/lib/request/backend";
import { BELLA_OPENAPI_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";
import { NextResponse } from "next/server";

export async function GET(req: NextRequest) {
  if (process.env.BELLA_DEV_AUTH === "true") {
    const spaceCode = process.env.BELLA_DEV_SPACE_CODE || "local-dev";
    return NextResponse.json({
      code: 200,
      data: [
        {
          roleCode: "owner",
          spaceCode,
          spaceName: process.env.BELLA_DEV_SPACE_NAME || "Local Dev",
        },
      ],
      message: "",
    });
  }

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
