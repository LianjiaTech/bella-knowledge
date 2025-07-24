import { webRequest, webRequestFormData } from "@/lib/request/web";
import { KnowledgeFile } from "@/lib/types/file";

export async function getFileList() {
  const res = await webRequest<{ data: KnowledgeFile[] }>({
    path: "/api/files",
    method: "GET",
  });
  if (res.code === 200) {
    return res.data.data;
  }
  return [];
}

export async function postUploadFile(file: File) {
  const res = await webRequestFormData<KnowledgeFile>({
    path: "/api/files",
    data: {
      file,
    },
  });
  if (res.code === 200) {
    return res.data;
  }
  return null;
}

export async function getFileProgress(fileId: string) {
  const res = await webRequest<{
    percent: number;
    code: number;
    status: "document_parse_finish" | "document_parse_failed";
  }>({
    path: "/api/dom-tree/progress",
    method: "GET",
    query: {
      fileId: fileId,
    },
  });
  if (res.code === 200) {
    return res.data;
  }
  return null;
}

export async function postCropImage(
  fileId: string,
  bbox: [number, number, number, number],
  page: number,
) {
  const res = await webRequest<{
    image_base64: string;
  }>({
    path: "/api/files/crop-image",
    method: "POST",
    body: {
      file_id: fileId,
      bbox,
      page,
    },
  });
  if (res.code === 200) {
    return res.data;
  }
  return null;
}
