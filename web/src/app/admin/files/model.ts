import { KnowledgeFile } from "@/lib/types/file";
import {
  findFiles,
  postCreateFolder,
  postRenameFile,
  postUploadFile,
  deleteFile,
  updateFileContent,
} from "@/request/files";
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
  initPage: (spaceCode?: string) => Promise<void>;
  createFolder: (values: { name: string }, spaceCode?: string) => Promise<boolean>;
  enterFolder: (file: KnowledgeFile, spaceCode?: string) => Promise<void>;
  jumpFolder: (id: string, spaceCode?: string) => Promise<void>;
  backFolder: () => void;
  uploadFile: (file: File, ancestor_id: string) => Promise<boolean>;
  renameFile: (
    file: KnowledgeFile,
    filename: string,
    ancestorId: string,
  ) => Promise<boolean>;
  deleteFile: (file: KnowledgeFile, ancestorId: string) => Promise<boolean>;
  reUploadFile: (fileId: string, file: File, ancestorId: string) => Promise<boolean>;
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
  initPage: async (spaceCode?: string) => {
    const res = await findFiles({ ancestor_id: "", space_code: spaceCode });
    set({
      files: { "": res.data },
    });
  },
  createFolder: async (values: { name: string }, spaceCode?: string) => {
    const currentDirStack = get().currentDirStack;
    const currentDir = currentDirStack[currentDirStack.length - 1];

    const res = await postCreateFolder({
      name: values.name,
      ancestor_id: currentDir.id,
    });
    if (res) {
      const fileRes = await findFiles({ ancestor_id: currentDir.id, space_code: spaceCode });
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
  enterFolder: async (file: KnowledgeFile, spaceCode?: string) => {
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
    const res = await findFiles({ ancestor_id: id, space_code: spaceCode });
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
  jumpFolder: async (id: string, spaceCode?: string) => {
    const { currentDirStack } = get();
    const dirIndex = currentDirStack.findIndex((d) => d.id === id);
    const currentDir = currentDirStack[currentDirStack.length - 1];
    if (dirIndex !== -1) {
      set({ currentDirStack: currentDirStack.slice(0, dirIndex + 1) });
    }
    if (id === currentDir.id) {
      return;
    }
    const res = await findFiles({ ancestor_id: id, space_code: spaceCode });
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
  renameFile: async (file: KnowledgeFile, filename: string, ancestorId: string) => {
    const res = await postRenameFile(file.id, filename);
    if (res) {
      set((state) => {
        const siblingFiles = state.files[ancestorId] || [];
        const newFiles = {
          ...state.files,
          [ancestorId]: siblingFiles.map((item) =>
            item.id === file.id ? {
              ...item,
              // 使用后端返回的完整信息更新
              filename: res.filename,
              extension: res.extension,
              // 其他可能变化的字段也同步更新
              mime_type: res.mime_type || item.mime_type,
              bytes: res.bytes || item.bytes,
            } : item,
          ),
        };
        const newCurrentDirStack = state.currentDirStack.map((dir) =>
          dir.id === file.id ? { ...dir, name: res.filename } : dir,
        );
        return {
          files: newFiles,
          currentDirStack: newCurrentDirStack,
        };
      });
      return true;
    }
    return false;
  },
  deleteFile: async (file: KnowledgeFile, ancestorId: string) => {
    const res = await deleteFile(file.id);
    if (res) {
      set((state) => {
        const siblingFiles = state.files[ancestorId] || [];
        const newFiles = {
          ...state.files,
          [ancestorId]: siblingFiles.filter((item) => item.id !== file.id),
        };
        return {
          files: newFiles,
        };
      });
      return true;
    }
    return false;
  },
  reUploadFile: async (fileId: string, file: File, ancestorId: string) => {
    const res = await updateFileContent(fileId, file);
    if (res) {
      set((state) => {
        const siblingFiles = state.files[ancestorId] || [];
        const newFiles = {
          ...state.files,
          [ancestorId]: siblingFiles.map((item) =>
            item.id === fileId ? {
              ...item,
              // 使用后端返回的完整信息更新，特别是文件相关属性
              filename: res.filename,
              extension: res.extension,
              mime_type: res.mime_type,
              bytes: res.bytes,
              type: res.type,
              // 更新时间相关字段
              created_at: res.created_at || item.created_at,
              // 其他字段
              purpose: res.purpose || item.purpose,
              dom_tree_file_id: res.dom_tree_file_id || item.dom_tree_file_id,
            } : item,
          ),
        };
        return {
          files: newFiles,
        };
      });
      return true;
    }
    return false;
  },
}));

export const useModel = () => store(useShallow((state) => state));
