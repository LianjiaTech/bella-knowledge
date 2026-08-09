import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";
import { NextResponse } from "next/server";

export async function GET(req: NextRequest) {
  if (process.env.BELLA_DEV_AUTH === "true") {
    return NextResponse.json({
      code: 200,
      data: {
        userId: process.env.BELLA_DEV_USER_ID || "1",
        userName: process.env.BELLA_DEV_USER_NAME || "Local Dev",
        spaceCode: process.env.BELLA_DEV_SPACE_CODE || "local-dev",
      },
      message: "",
    });
  }

  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/openapi/userInfo`,
    method: "GET",
  });
  return res;
}
