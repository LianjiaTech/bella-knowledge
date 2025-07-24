import { NextRequest, NextResponse } from "next/server";
import { LogUtils } from "@/lib/utils/log-utils";

const isProd = process.env.NODE_ENV === "production";

export async function backendRequest(
  req: NextRequest,
  params: {
    url: string;
    method: "GET" | "POST";
    query?: Record<string, string>;
    body?: Record<string, unknown>;
  },
) {
  try {
    const { url, method, query, body } = params;
    const workspace = req.headers.get("X-BELLA-SPACE-CODE");
    const response = await fetch(
      url + (query ? "?" + new URLSearchParams(query).toString() : ""),
      {
        headers: {
          "X-BELLA-CONSOLE": "true",
          "X-BELLA-SPACE-CODE": workspace || "",
          "Content-Type": "application/json",
          cookie: req.cookies.toString(),
        },
        method,
        body: JSON.stringify(body),
      },
    );

    logRequestError(req, { url, method, query, body }, response);
    if (response.status === 401) {
      if (url.includes("/userInfo") || url.includes("/role/list")) {
      }
      const redirectUrl = response.headers.get("X-Redirect-Login");
      LogUtils.warning(`用户未授权，需要重新登录: ${req.url}`);
      return NextResponse.json({
        code: 401,
        data: {
          redirectUrl: redirectUrl,
        },
      });
    }

    if (response.status !== 200) {
      logRequestError(req, { url, method, query, body }, response);

      const data = await response.json();
      return NextResponse.json(data);
    } else {
      if (
        url.includes("/dom-tree/content") ||
        url.includes("/role/list") ||
        url.includes("/userInfo")
      ) {
        logRequestError(req, { url, method, query, body }, response);
      }
      const responseData = await response.json();
      LogUtils.success(`接口请求成功: ${method} ${url}`);
      return NextResponse.json(responseData);
    }
  } catch (error) {
    return NextResponse.json({
      code: 500,
      message: error instanceof Error ? error.message : "未知错误",
    });
  }
}

export async function backendRequestFormData(
  req: NextRequest,
  params: {
    method: "GET" | "POST" | "PUT";
    url: string;
    data?: Record<string, unknown>;
  },
) {
  const { url, data, method } = params;
  const formData = new FormData();
  if (data) {
    Object.entries(data).forEach(([key, value]) => {
      formData.append(key, value as string);
    });
  }
  const workspace = req.headers.get("X-BELLA-SPACE-CODE");
  const response = await fetch(url, {
    headers: {
      "X-BELLA-CONSOLE": "true",
      "X-BELLA-SPACE-CODE": workspace || "",
      cookie: req.cookies.toString(),
    },
    body: formData,
    method,
  });
  return response;
}

const logRequestError = (
  req: NextRequest,
  params: {
    url: string;
    method: "GET" | "POST";
    query?: Record<string, string>;
    body?: Record<string, unknown>;
  },
  response: Response,
) => {
  if (isProd) return;
  const { url, method, query, body } = params;
  // 生成调试 curl 命令
  const cookies = req.cookies.getAll();
  const cookieHeader =
    cookies.length > 0
      ? `-H "Cookie: ${cookies.map((c) => `${c.name}=${c.value}`).join("; ")}"`
      : "";

  const queryString = query ? `?${new URLSearchParams(query).toString()}` : "";
  const bodyParam = body ? `-d '${JSON.stringify(body)}' \\\n  ` : "";
  const workspace = req.headers.get("X-BELLA-SPACE-CODE");
  const curl = `curl -X ${method} \\\n  -H "X-BELLA-CONSOLE: true" \\\n  -H "X-BELLA-SPACE-CODE: ${workspace}" \\\n  -H "Content-Type: application/json" \\\n  ${cookieHeader}${
    cookieHeader ? " \\\n  " : ""
  }${bodyParam}${url}${queryString}`;

  LogUtils.error(`${method} ${url} ${response.status}\n${curl}`);
};
