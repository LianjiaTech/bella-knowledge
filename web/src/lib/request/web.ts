import { toast } from "sonner";

interface WebResponse<T> {
  code: number;
  data: T;
  message: string;
}
interface WebRequestParams {
  path: string;
  method: "GET" | "POST" | "PUT" | "DELETE";
  query?: Record<string, string>;
  body?: Record<string, unknown>;
  headers?: Record<string, string>;
}
export async function webRequest<T>(
  params: WebRequestParams,
): Promise<WebResponse<T>> {
  const { path, method, query, body, headers } = params;
  const currentWorkspace = JSON.parse(
    localStorage.getItem("current_workspace") || "{}",
  );
  const response = await fetch(
    path + (query ? "?" + new URLSearchParams(query).toString() : ""),
    {
      credentials: "same-origin",
      method,
      body: JSON.stringify(body),
      headers: {
        "Content-Type": "application/json",
        "X-USER-ID": localStorage.getItem("user_id") || "",
        "X-BELLA-SPACE-CODE": currentWorkspace.spaceCode || "",
        ...headers,
      },
    },
  );

  const data = await response.json();
  if (data.code === 401) {
    window.location.href =
      data.data.redirectUrl + encodeURIComponent(window.location.href);
  }
  if (data.code === 500) {
    toast.error(data.message);
    return {
      code: 500,
      data: null,
      message: data.message,
    } as WebResponse<T>;
  }
  return data as WebResponse<T>;
}

export async function webRequestFormData<T>(params: {
  path: string;
  data: Record<string, unknown>;
  headers?: Record<string, string>;
}): Promise<WebResponse<T>> {
  const { path, data, headers } = params;
  const formData = new FormData();
  Object.entries(data).forEach(([key, value]) => {
    formData.append(key, value as string);
  });
  const currentWorkspace = JSON.parse(
    localStorage.getItem("current_workspace") || "{}",
  );
  const response = await fetch(path, {
    credentials: "same-origin",
    method: "POST",
    body: formData,
    headers: {
      "X-USER-ID": localStorage.getItem("user_id") || "",
      "X-BELLA-SPACE-CODE": currentWorkspace.spaceCode || "",
      ...headers,
    },
  });
  const responseData = await response.json();
  if (responseData.code === 401) {
    window.location.href =
      responseData.data.redirectUrl + encodeURIComponent(window.location.href);
    return {
      code: 401,
      data: null,
      message: responseData.message,
    } as WebResponse<T>;
  }
  if (responseData.code !== 200) {
    toast.error(responseData.message);
    return {
      code: responseData.code,
      data: null,
      message: responseData.message,
    } as WebResponse<T>;
  }
  return responseData as WebResponse<T>;
}

export async function webRequestFetch(url: string) {
  try {
    const response = await fetch(url);
    return response.json();
  } catch {
    return null;
  }
}
