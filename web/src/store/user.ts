import { webRequest } from "@/lib/request/web";
import { UserInfo, Workspace } from "@/lib/types/user";
import { create } from "zustand";
import { useShallow } from "zustand/shallow";

const store = create<{
  userInfo: UserInfo | null;
  getUserInfo: () => Promise<void>;
  workspaceList: Workspace[];
  currentWorkspace: Workspace | null;
  changeCurrentWorkspace: (spaceCode: string) => void;
  getWorkspaceList: () => Promise<void>;
}>((set, get) => ({
  userInfo: null,
  workspaceList: [],
  currentWorkspace: null,
  getUserInfo: async () => {
    const { userInfo } = get();
    if (userInfo) {
      return;
    }
    const res = await webRequest<UserInfo>({
      path: "/api/user-info",
      method: "GET",
    });
    if (res.data.userId) {
      localStorage.setItem("user_id", res.data.userId);
      set({ userInfo: res.data });
    }
  },
  getWorkspaceList: async () => {
    const { workspaceList } = get();
    if (workspaceList.length > 0) {
      return;
    }
    const res = await webRequest<
      {
        roleCode: string;
        spaceCode: string;
        spaceName: string;
      }[]
    >({
      path: "/api/work-space",
      method: "GET",
    });
    set({ workspaceList: res.data });
    const currentWorkspace = localStorage.getItem("current_workspace");
    if (currentWorkspace) {
      set({ currentWorkspace: JSON.parse(currentWorkspace) });
    } else if (res.data.length > 0) {
      localStorage.setItem("current_workspace", JSON.stringify(res.data[0]));
      set({ currentWorkspace: res.data[0] });
    }
  },
  changeCurrentWorkspace: (spaceCode: string) => {
    const { workspaceList } = get();
    const workspace = workspaceList.find(
      (space) => space.spaceCode === spaceCode
    );
    if (workspace) {
      localStorage.setItem("current_workspace", JSON.stringify(workspace));
      set({ currentWorkspace: workspace });
    }
  },
}));

export const useUserStore = () => store(useShallow((state) => state));
