import { create } from "zustand";
import { useShallow } from "zustand/shallow";
import { KnowledgeFile } from "@/lib/types/file";
import { DocumentData } from "@/lib/types/documents";
import { Block, BlockNoteEditor } from "@blocknote/core";
import { blocksToDocumentData } from "@/lib/document-parser";
import { getFileList, postCropImage } from "@/request/files";
import {
  requestAddDatasetFile,
  requestGetDatasetFileList,
  requestDeleteDatasetFile,
} from "@/request/dataset";
import { DatasetFile } from "@/lib/types/file";
import { requestDomTree, requestUpdateDomTree } from "@/request/dom-tree";

type BboxPosition = {
  bbox: [number, number, number, number];
  page: number;
  id: string;
  type: "auto" | "manual"; // auto来自编辑器，manual来自手动绘制
};

type State = {
  fileList: KnowledgeFile[];
  datasetFileList: DatasetFile[];
  selectedFile: DatasetFile | null;
  domData: DocumentData | null;
  // 编辑器内随着编辑实时更新的DomData
  editingDomData: DocumentData | null;
  documentContent: {
    fileId: string;
    type: "pdf" | "docx";
    name: string;
  } | null;
  currentEditingBlock: Block | null;
  currentEditingPositions: BboxPosition[] | null;
  allBlockPositions: Record<string, BboxPosition[]>; // 保存所有块的位置数据
  isDrawingMode: boolean;
  editor: BlockNoteEditor | null;
  lastSaveTime: string | null;
  currentPdfPage: number; // 当前PDF页码
  isScreenshotMode: boolean; // 截图模式
  actionStack: ActionRecord[];
  screenshotCallback:
    | ((
        imageUrl: string,
        bbox: [number, number, number, number],
        page: number,
      ) => void)
    | null; // 截图完成回调
  activePositionOverlay: {
    index: number;
    position: BboxPosition;
  } | null;
  screenShotLoading: boolean;
};

type Action = {
  getFileList: () => Promise<void>;
  getDatasetFileList: (datasetId: string) => Promise<void>;
  selectDatasetFile: (file: DatasetFile) => Promise<boolean>;
  saveDomData: () => Promise<boolean>;
  addUploadFile: (file: KnowledgeFile) => void;
  addDatasetFile: (datasetId: string, fileIds: string[]) => Promise<void>;
  deleteDatasetFile: (datasetId: string, fileId: string) => Promise<void>;
  focusBlock: (block: Block, positions?: BboxPosition[] | null) => void;
  clearFocusedBlock: () => void;
  attachPositionsToBlock: (block: Block, positions: BboxPosition[]) => void;
  addManualPosition: (position: BboxPosition) => void;
  updatePosition: (id: string, bbox: [number, number, number, number]) => void;
  updatePositionPage: (id: string, page: number) => void;
  removePosition: (id: string) => void;
  setDrawingMode: (mode: boolean) => void;
  initEditor: (editor: BlockNoteEditor) => void;
  setPdfPage: (page: number) => void;
  setScreenshotMode: (
    mode: boolean,
    callback?: (
      imageUrl: string,
      bbox: [number, number, number, number],
      page: number,
    ) => void,
  ) => void;
  completeScreenshot: (
    bbox: [number, number, number, number],
    page: number,
  ) => Promise<boolean>;
  undoLastAction: () => void;
  recordAction: (action: ActionRecord) => void;
  clickPositionOverlay: (data: {
    index: number;
    position: BboxPosition;
  }) => void;
  updateEditingDomData: (domData: DocumentData) => void;
  clearModel: () => void;
};

type ActionRecord = {
  type: "add" | "edit" | "delete";
  position: BboxPosition;
  previousBbox?: [number, number, number, number];
  deleteIndex?: number;
};

export const store = create<State & Action>((set, get) => ({
  fileList: [],
  datasetFileList: [],
  selectedFile: null,
  domData: null,
  editingDomData: null,
  documentContent: null,
  currentEditingBlock: null,
  currentEditingPositions: null,
  allBlockPositions: {},
  isDrawingMode: false,
  editor: null,
  lastSaveTime: null,
  currentPdfPage: 1,
  isScreenshotMode: false,
  screenshotCallback: null,
  actionStack: [],
  activePositionOverlay: null,
  screenShotLoading: false,
  getFileList: async () => {
    const res = await getFileList();
    set({ fileList: res });
  },
  getDatasetFileList: async (datasetId: string) => {
    const res = await requestGetDatasetFileList(datasetId);
    if (res.code === 200) {
      set({ datasetFileList: res.data });
    }
  },
  addUploadFile: (file: KnowledgeFile) => {
    set({ fileList: [file, ...get().fileList] });
  },
  addDatasetFile: async (datasetId: string, fileIds: string[]) => {
    const res = await requestAddDatasetFile(datasetId, fileIds);
    if (res.code === 200) {
      set((state) => {
        return {
          datasetFileList: [...res.data, ...state.datasetFileList],
        };
      });
    }
  },
  deleteDatasetFile: async (datasetId: string, fileId: string) => {
    const res = await requestDeleteDatasetFile(datasetId, fileId);
    if (res.code === 200) {
      set((state) => {
        return {
          datasetFileList: state.datasetFileList.filter(
            (f) => f.file_id !== fileId,
          ),
        };
      });
    }
  },
  selectDatasetFile: async (file: DatasetFile) => {
    const { fileList } = get();
    const fileId = file.file_id;
    set({
      selectedFile: file,
    });
    const domData = await requestDomTree(fileId);
    if (domData) {
      set({
        domData,
        editingDomData: domData,
        documentContent: {
          fileId,
          type: fileList.find((f) => f.id === fileId)?.extension as
            | "pdf"
            | "docx",
          name: fileList.find((f) => f.id === fileId)?.filename || "",
        },
        allBlockPositions: {},
        currentEditingBlock: null,
        currentEditingPositions: null,
        lastSaveTime: null,
      });
      return true;
    } else {
      set({
        domData: null,
        documentContent: null,
        lastSaveTime: null,
      });
      return false;
    }
  },
  saveDomData: async () => {
    const { selectedFile, fileList, allBlockPositions, editor, domData } =
      get();
    const blocks = editor?.document || [];

    // 将allBlockPositions中的位置信息合并到对应的block中
    const blocksWithPositions = blocks.map((block) => {
      const blockId = block.id;
      const positions = allBlockPositions[blockId];
      return {
        ...block,
        props: {
          ...block.props,
          positions: positions
            ? positions?.map((pos) => ({
                bbox: pos.bbox,
                page: pos.page,
              }))
            : undefined,
        } as Record<string, unknown>,
      } as Block;
    });

    // 使用包含位置信息的blocks转换为DocumentData
    const newDomData = blocksToDocumentData(blocksWithPositions, domData!);
    const res = await requestUpdateDomTree(
      fileList.find((f) => f.id === selectedFile!.file_id)?.dom_tree_file_id ||
        "",
      newDomData,
    );

    if (res) {
      set({ lastSaveTime: new Date().toLocaleString() });
      return true;
    }
    return false;
  },
  focusBlock: (block: Block, positions?: BboxPosition[] | null) => {
    set((state) => {
      const blockId = block.id;
      const existingPositions = state.allBlockPositions[blockId];

      if (existingPositions && existingPositions.length > 0) {
        return {
          currentEditingBlock: block,
          currentEditingPositions: existingPositions,
        };
      } else {
        const newPositions = positions || [];
        return {
          currentEditingBlock: block,
          currentEditingPositions: newPositions,
          allBlockPositions: {
            ...state.allBlockPositions,
            [blockId]: newPositions,
          },
        };
      }
    });
  },
  clearFocusedBlock: () => {
    set({ currentEditingBlock: null, currentEditingPositions: null });
  },
  attachPositionsToBlock: (block: Block, positions: BboxPosition[]) => {
    set((state) => {
      const blockId = block.id;
      return {
        currentEditingBlock: block,
        currentEditingPositions: positions,
        allBlockPositions: {
          ...state.allBlockPositions,
          [blockId]: positions,
        },
      };
    });
  },
  addManualPosition: (position: BboxPosition) => {
    set((state) => {
      const existing = state.currentEditingPositions || [];
      const newPositions = [...existing, position];

      // 同时更新allBlockPositions
      const currentBlockId = state.currentEditingBlock?.id;
      const newAllBlockPositions = currentBlockId
        ? {
            ...state.allBlockPositions,
            [currentBlockId]: newPositions,
          }
        : state.allBlockPositions;

      return {
        currentEditingPositions: newPositions,
        allBlockPositions: newAllBlockPositions,
        activePositionOverlay: {
          index: newPositions.length - 1,
          position,
        },
      };
    });
  },
  updatePosition: (id: string, bbox: [number, number, number, number]) => {
    set((state) => {
      const positions = state.currentEditingPositions?.map((pos) => {
        if (pos.id === id) {
          return { ...pos, bbox };
        }
        return pos;
      });

      // 同时更新allBlockPositions
      const currentBlockId = state.currentEditingBlock?.id;
      const newAllBlockPositions =
        currentBlockId && positions
          ? {
              ...state.allBlockPositions,
              [currentBlockId]: positions,
            }
          : state.allBlockPositions;

      return {
        currentEditingPositions: positions || null,
        allBlockPositions: newAllBlockPositions,
      };
    });
  },
  removePosition: (id: string) => {
    set((state) => {
      const positions = state.currentEditingPositions?.filter(
        (pos) => pos.id !== id,
      );

      // 同时更新allBlockPositions
      const currentBlockId = state.currentEditingBlock?.id;
      const newAllBlockPositions = currentBlockId
        ? {
            ...state.allBlockPositions,
            [currentBlockId]: positions?.length ? positions : [],
          }
        : state.allBlockPositions;

      return {
        currentEditingPositions: positions?.length ? positions : [],
        allBlockPositions: newAllBlockPositions,
      };
    });
  },
  setDrawingMode: (mode: boolean) => {
    set({ isDrawingMode: mode });
  },
  updatePositionPage: (id: string, page: number) => {
    set((state) => {
      const positions = state.currentEditingPositions?.map((pos) =>
        pos.id === id ? { ...pos, page } : pos,
      );

      // 同时更新allBlockPositions
      const currentBlockId = state.currentEditingBlock?.id;
      const newAllBlockPositions =
        currentBlockId && positions
          ? {
              ...state.allBlockPositions,
              [currentBlockId]: positions,
            }
          : state.allBlockPositions;

      return {
        currentEditingPositions: positions || null,
        allBlockPositions: newAllBlockPositions,
      };
    });
  },
  initEditor: (editor) => {
    set({ editor });
  },
  setPdfPage: (page: number) => {
    set({ currentPdfPage: page });
  },
  setScreenshotMode: (
    mode: boolean,
    callback?: (
      imageUrl: string,
      bbox: [number, number, number, number],
      page: number,
    ) => void,
  ) => {
    set({
      isDrawingMode: mode ? false : get().isDrawingMode,
      isScreenshotMode: mode,
      screenshotCallback: callback || null,
    });
  },
  completeScreenshot: async (
    bbox: [number, number, number, number],
    page: number,
  ) => {
    set({ isScreenshotMode: false });
    const { screenshotCallback } = get();
    const { selectedFile } = get();
    const fileId = selectedFile?.file_id || "";
    set({ screenShotLoading: true });
    const res = await postCropImage(fileId, bbox, page);
    if (res?.image_base64) {
      if (screenshotCallback) {
        screenshotCallback(res.image_base64, bbox, page);
        set({ screenshotCallback: null, screenShotLoading: false });
        return true;
      }
    }
    set({ screenShotLoading: false });
    return false;
  },
  undoLastAction: () => {
    const lastAction =
      get().actionStack.length > 0
        ? get().actionStack[get().actionStack.length - 1]
        : null;
    if (!lastAction) return;

    set((state: State) => {
      if (lastAction.type === "add") {
        // Remove the last added position
        const newPositions =
          state.currentEditingPositions?.filter(
            (pos: BboxPosition) => pos.id !== lastAction.position.id,
          ) || null;

        return {
          currentEditingPositions: newPositions,
          allBlockPositions: {
            ...state.allBlockPositions,
            [state.currentEditingBlock?.id || ""]: newPositions || [],
          },
          actionStack: state.actionStack.slice(0, -1),
        };
      } else if (lastAction.type === "edit" && lastAction.previousBbox) {
        const newPositions =
          state.currentEditingPositions?.map((pos: BboxPosition) =>
            pos.id === lastAction.position.id
              ? {
                  ...pos,
                  bbox: lastAction.previousBbox as [
                    number,
                    number,
                    number,
                    number,
                  ],
                }
              : pos,
          ) || null;

        return {
          currentEditingPositions: newPositions,
          allBlockPositions: {
            ...state.allBlockPositions,
            [state.currentEditingBlock?.id || ""]: newPositions || [],
          },
          actionStack: state.actionStack.slice(0, -1),
        };
      } else if (lastAction.type === "delete") {
        const deleteIndex = lastAction.deleteIndex!;
        const currentPositions = state.currentEditingPositions;
        const newPositions = [
          ...currentPositions!.slice(0, deleteIndex),
          lastAction.position,
          ...currentPositions!.slice(deleteIndex),
        ];
        return {
          currentEditingPositions: newPositions,
          allBlockPositions: {
            ...state.allBlockPositions,
            [state.currentEditingBlock?.id || ""]: newPositions || [],
          },
          actionStack: state.actionStack.slice(0, -1),
        };
      }
      return state;
    });
  },
  recordAction: (action: ActionRecord) => {
    const newAction = JSON.parse(JSON.stringify(action));
    set((state) => ({ actionStack: [...state.actionStack, newAction] }));
  },
  clickPositionOverlay: (data: { index: number; position: BboxPosition }) => {
    set(() => {
      return {
        activePositionOverlay: data,
      };
    });
  },
  updateEditingDomData: (domData: DocumentData) => {
    set({ editingDomData: domData });
  },
  clearModel: () => {
    set({
      fileList: [],
      datasetFileList: [],
      selectedFile: null,
      domData: null,
      // 编辑器内随着编辑实时更新的DomData
      editingDomData: null,
      documentContent: null,
      currentEditingBlock: null,
      currentEditingPositions: null,
      allBlockPositions: {}, // 保存所有块的位置数据
      isDrawingMode: false,
      editor: null,
      lastSaveTime: null,
      currentPdfPage: 1,
      isScreenshotMode: false, // 截图模式
      actionStack: [],
      screenshotCallback: null,
      activePositionOverlay: null,
      screenShotLoading: false,
    });
  },
}));
export const useDocumentParserStore = () => store(useShallow((state) => state));
