import { webRequest } from "@/lib/request/web";
import { Dataset, QuestionList } from "@/lib/types/qa";
import { DatasetFile } from "@/lib/types/file";

export const requestGetDatasetQuestionList = async (datasetId: string) => {
  const res = await webRequest<QuestionList>({
    path: `/api/qa/list`,
    method: "GET",
    query: {
      dataset_id: datasetId,
    },
  });
  return res;
};

export const requestDatasetList = async (query: {
  page: number;
  pageSize: number;
  type: "qa" | "document";
}) => {
  const res = await webRequest<{
    data: Dataset[];
    total?: number;
    page: number;
    limit: number;
    has_more: boolean;
  }>({
    path: "/api/dataset/page",
    method: "GET",
    query: {
      page: query.page.toString(),
      page_size: query.pageSize.toString(),
      type: query.type,
    },
  });
  return res;
};

export const requestCreateDataset = async (data: {
  name: string;
  type: "qa" | "document";
  remark: string;
  file_id?: string;
}) => {
  const res = await webRequest({
    path: "/api/dataset",
    method: "POST",
    body: data,
  });
  return res;
};

export const requestDeleteDataset = async (datasetId: string) => {
  const res = await webRequest({
    path: `/api/dataset`,
    method: "DELETE",
    body: {
      dataset_id: datasetId,
    },
  });
  return res;
};

export const requestGetDatasetFileList = async (datasetId: string) => {
  const res = await webRequest<DatasetFile[]>({
    path: `/api/document`,
    method: "GET",
    query: {
      dataset_id: datasetId,
    },
  });
  return res;
};

export const requestAddDatasetFile = async (
  datasetId: string,
  fileIds: string[],
) => {
  const res = await webRequest<DatasetFile[]>({
    path: `/api/document`,
    method: "POST",
    body: {
      dataset_id: datasetId,
      file_ids: fileIds,
    },
  });
  return res;
};

export const requestDeleteDatasetFile = async (
  datasetId: string,
  fileId: string,
) => {
  const res = await webRequest({
    path: `/api/document`,
    method: "DELETE",
    body: {
      dataset_id: datasetId,
      file_id: fileId,
    },
  });
  return res;
};

export const getDocumentContentUrl = async (fileId: string) => {
  const res = await webRequest<{
    url: string;
  }>({
    path: `/api/document/url`,
    method: "GET",
    query: {
      file_id: fileId,
    },
  });
  return res;
};

export const requestUpdateDatasetRemark = async (
  datasetId: string,
  remark: string,
) => {
  const res = await webRequest<Dataset>({
    path: `/api/dataset`,
    method: "PUT",
    body: {
      dataset_id: datasetId,
      remark,
    },
  });
  if (res.code === 200) {
    return res.data;
  }
  return res.message;
};
