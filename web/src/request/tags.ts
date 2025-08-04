import { webRequest } from "@/lib/request/web";

export interface Tag {
  id: number;
  space_code: string;
  name: string;
  cuid: number;
  cu_name: string;
  ctime: string;
  muid: number;
  mu_name: string;
  mtime: string;
  status: number;
}

export const requestTagsList = async () => {
  const res = await webRequest<Tag[]>({
    path: "/api/tags/list",
    method: "POST",
    body: {},
  });
  return res;
};

export const requestCreateTag = async (name: string) => {
  const res = await webRequest<Tag>({
    path: "/api/tags/create",
    method: "POST",
    body: { name },
  });
  return res;
};