import { NextRequest, NextResponse } from "next/server";
import { LogUtils } from "@/lib/utils/log-utils";

const isProd = process.env.NODE_ENV === "production";

export async function backendRequest(
  req: NextRequest,
  params: {
    url: string;
    method: "GET" | "POST" | "DELETE";
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
    // 每个接口都打印一下，方便调试
    logRequestError(req, { url, method, query, body }, response);

    if (response.status === 401) {
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
      try {
        const data = await response.json();
        if (data.error) {
          return NextResponse.json({
            code: 500,
            error: true,
            message: data.error.message,
          });
        }
        return NextResponse.json({
          code: 500,
          error: true,
          message: "未知错误",
        });
      } catch (error) {
        return NextResponse.json({
          code: 500,
          message: error instanceof Error ? error.message : "服务器错误",
        });
      }
    } else {
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
  if (response.status === 500) {
    logRequestErrorFormData(req, { url, method, body: data }, response);
    return NextResponse.json({
      code: 500,
      message: "服务器错误",
    });
  }

  if (response.status === 401) {
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
    logRequestErrorFormData(req, { url, method, body: data }, response);
    try {
      const resBody = await response.json();
      return NextResponse.json({
        code: response.status,
        message: resBody.error?.message || resBody.message || "请求失败",
      });
    } catch (error) {
      return NextResponse.json({
        code: response.status,
        message: `请求失败 (${response.status})`,
      });
    }
  }
  
  try {
    const responseData = await response.json();
    return NextResponse.json({
      code: 200,
      data: responseData,
    });
  } catch (error) {
    LogUtils.error(`JSON解析失败: ${error}`);
    return NextResponse.json({
      code: 500,
      message: "响应格式错误",
    });
  }
}

const logRequestError = (
  req: NextRequest,
  params: {
    url: string;
    method: "GET" | "POST" | "DELETE";
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

const logRequestErrorFormData = (
  req: NextRequest,
  params: {
    url: string;
    method: "GET" | "POST" | "PUT";
    query?: Record<string, string>;
    body?: Record<string, unknown>;
  },
  response: Response,
) => {
  if (isProd) return;
  const { url, method, query, body } = params;
  const cookies = req.cookies.getAll();
  const cookieHeader =
    cookies.length > 0
      ? `-H "Cookie: ${cookies.map((c) => `${c.name}=${c.value}`).join("; ")}"`
      : "";
  const queryString = query ? `?${new URLSearchParams(query).toString()}` : "";
  const bodyParams = Object.entries(body || {}).map(
    ([key, value]) => `-F '${key}=${value}' \\\n  `,
  );
  const workspace = req.headers.get("X-BELLA-SPACE-CODE");
  const curl = `curl -X ${method} \\\n  -H "X-BELLA-CONSOLE: true" \\\n  -H "X-BELLA-SPACE-CODE: ${workspace}" \\\n  -H "Content-Type: multipart/form-data" \\\n  ${cookieHeader}${
    cookieHeader ? " \\\n  " : ""
  }${bodyParams.join("")}${url}${queryString}`;
  LogUtils.error(`${method} ${url} ${response.status}\n${curl}`);
};
