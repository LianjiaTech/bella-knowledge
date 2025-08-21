import { webRequest, webRequestFormData } from "@/lib/request/web";
import { KnowledgeFile } from "@/lib/types/file";
import { anOldHope } from "react-syntax-highlighter/dist/esm/styles/hljs";

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

type FindFilesParams = {
  ancestor_id?: string;
};

export async function findFiles(params: FindFilesParams) {
  const res = await webRequest<{
    data: KnowledgeFile[];
  }>({
    path: "/api/files/find",
    method: "GET",
    query: {
      ...params,
    },
  });
  if (res.code === 200) {
    return {
      ...res.data,
      data: res.data.data.sort((a, b) => {
        if (a.is_dir && !b.is_dir) {
          return -1;
        }
        if (!a.is_dir && b.is_dir) {
          return 1;
        }
        return b.created_at - a.created_at;
      }),
    };
  }
  return {
    data: [],
  };
}

export async function postCreateFolder(body: {
  name: string;
  ancestor_id: string;
}) {
  const res = await webRequest<KnowledgeFile>({
    path: "/api/files/mkdir",
    method: "POST",
    body,
  });
  if (res.code === 200) {
    return res.data;
  }
  return null;
}

export async function postUploadFile(data: {
  file: File;
  purpose?: string;
  ancestor_id?: string;
}) {
  const res = await webRequestFormData<KnowledgeFile>({
    path: "/api/files",
    data,
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

export async function getFilePreviewUrl(fileId: string) {
  const res = await webRequest<{ url: string }>({
    path: "/api/files/preview",
    method: "GET",
    query: {
      fileId,
    },
  });
  if (res.code === 200) {
    return res.data;
  }
  return null;
}
