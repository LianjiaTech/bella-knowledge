import { webRequest } from "@/lib/request/web";

export async function requestCreateQaReference(data: {
  dataset_id: string;
  item_id: string;
  file_id: string;
  path: number[];
  snippet: string;
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
