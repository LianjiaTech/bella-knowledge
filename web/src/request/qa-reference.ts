import { webRequest } from "@/lib/request/web";

export async function requestCreateQaReference(data: {
  dataset_id: string;
  item_id: string;
  file_id: string;
  path: number[];
  snippet: string;
  primary?: number;
  children_references?: number[];
}) {
  const res = await webRequest<{ reference_id: number }>({
    path: "/api/qa-reference",
    method: "POST",
    body: data,
  });
  if (res.code === 200) {
    return { result: true, data: res.data };
  }
  return {
    result: false,
    message: res.message,
  };
}

export async function requestUpdateQaReference(data: {
  dataset_id: string;
  reference_id: number;
  path: number[];
}) {
  const res = await webRequest({
    path: "/api/qa-reference",
    method: "PUT",
    body: data,
  });
  return res;
}

export async function requestUpdateQaReferencePrimary(data: {
  dataset_id: string;
  reference_id: string;
  primary: number;
}) {
  const res = await webRequest({
    path: "/api/qa-reference/primary",
    method: "PUT",
    body: data,
  });
  return res;
}
