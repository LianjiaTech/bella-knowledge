import { KnowledgeFile } from "@/lib/types/file";
import { findFiles, postCreateFolder, postUploadFile } from "@/request/files";
import { create } from "zustand";
import { useShallow } from "zustand/react/shallow";

// 需要设计文件夹的栈
// 并且需要存每个文件夹下有哪些文件
type State = {
  currentDirStack: {
    id: string;
    name: string;
  }[];
  files: {
    [dirId: string]: KnowledgeFile[];
  };
  tableLoading: boolean;
};
type Action = {
  initPage: () => Promise<void>;
  createFolder: (values: { name: string }) => Promise<boolean>;
  enterFolder: (file: KnowledgeFile) => Promise<void>;
  jumpFolder: (id: string) => Promise<void>;
  backFolder: () => void;
  uploadFile: (file: File, ancestor_id: string) => Promise<boolean>;
};
export const store = create<State & Action>()((set, get) => ({
  currentDirStack: [
    {
      id: "",
      name: "我的空间",
    },
  ],
  files: {
    "": [],
  },
  tableLoading: false,
  initPage: async () => {
    const res = await findFiles({ ancestor_id: "" });
    set({
      files: { "": res.data },
    });
  },
  createFolder: async (values: { name: string }) => {
    const currentDirStack = get().currentDirStack;
    const currentDir = currentDirStack[currentDirStack.length - 1];

    const res = await postCreateFolder({
      name: values.name,
      ancestor_id: currentDir.id,
    });
    if (res) {
      const fileRes = await findFiles({ ancestor_id: currentDir.id });
      if (fileRes) {
        set((state) => {
          const newFiles = {
            ...state.files,
            [currentDir.id]: fileRes.data,
          };
          return {
            files: newFiles,
          };
        });
      }

      return true;
    }
    return false;
  },
  enterFolder: async (file: KnowledgeFile) => {
    const { id, filename } = file;
    set((state) => {
      const newCurrentDirStack = [
        ...state.currentDirStack,
        { id, name: filename },
      ];
      return {
        currentDirStack: newCurrentDirStack,
        files: {
          ...state.files,
          [id]: state.files[id] || [],
        },
      };
    });
    set({ tableLoading: true });
    const res = await findFiles({ ancestor_id: id });
    set((state) => {
      const newFiles = {
        ...state.files,
        [id]: res.data,
      };
      return {
        files: newFiles,
        tableLoading: false,
      };
    });
  },
  jumpFolder: async (id: string) => {
    const { currentDirStack } = get();
    const dirIndex = currentDirStack.findIndex((d) => d.id === id);
    const currentDir = currentDirStack[currentDirStack.length - 1];
    if (dirIndex !== -1) {
      set({ currentDirStack: currentDirStack.slice(0, dirIndex + 1) });
    }
    if (id === currentDir.id) {
      return;
    }
    const res = await findFiles({ ancestor_id: id });
    set((state) => {
      const newFiles = {
        ...state.files,
        [id]: res.data,
      };
      return {
        files: newFiles,
      };
    });
  },
  backFolder: () => {
    set((state) => {
      const newCurrentDirStack = state.currentDirStack.slice(0, -1);
      return {
        currentDirStack: newCurrentDirStack,
      };
    });
  },
  uploadFile: async (file: File, ancestor_id: string) => {
    const res = await postUploadFile({ file, ancestor_id });
    if (res) {
      const newFiles = {
        ...get().files,
        [ancestor_id]: [res, ...get().files[ancestor_id]],
      };
      set({ files: newFiles });
      return true;
    }
    return false;
  },
}));

export const useModel = () => store(useShallow((state) => state));
