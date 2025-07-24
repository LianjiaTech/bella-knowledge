import {
  webRequest,
  webRequestFetch,
  webRequestFormData,
} from "@/lib/request/web";
import { DocumentData } from "@/lib/types/documents";

export async function requestDomTree(fileId: string) {
  const res = await webRequest<{ url: string }>({
    path: "/api/dom-tree",
    method: "GET",
    query: {
      fileId: fileId,
    },
  });
  if (res.data.url) {
    const url = res.data.url;
    const data = await webRequestFetch(url);
    if (data) {
      return data;
    }
    return null;
  }
  return null;
}

export async function requestUpdateDomTree(
  domTreeFileId: string,
  domData: DocumentData,
) {
  const file = new File([JSON.stringify(domData)], "file", {
    type: "text/plain",
  });
  const res = await webRequestFormData({
    path: "/api/dom-tree",
    data: {
      file_id: domTreeFileId,
      file,
    },
  });
  if (res.code === 200) {
    return true;
  }
  return false;
}
