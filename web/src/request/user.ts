import { webRequest } from "@/lib/request/web";
import { UserInfo } from "@/lib/types/user";

export const getUserInfo = async () => {
  const res = await webRequest<UserInfo>({
    path: "/api/user-info",
    method: "GET",
  });
  return res;
};
