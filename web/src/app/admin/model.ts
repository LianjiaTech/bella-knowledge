import { create } from "zustand";
import { useShallow } from "zustand/react/shallow";
import { Dataset } from "@/lib/types/qa";
import { requestDatasetList } from "@/request/dataset";

type State = {
  loading: boolean;
  datasetList: {
    qa: Dataset[];
    document: Dataset[];
  };
  currentPage: number;
  pageSize: number;
  total: number;
  hasMore: boolean;
  page: number;
};

type Action = {
  getDatasetList: (
    page?: number,
    pageSize?: number,
    type?: "qa" | "document",
  ) => Promise<void>;
  setCurrentPage: (page: number) => void;
  setPageSize: (pageSize: number) => void;
};

const store = create<State & Action>((set, get) => ({
  loading: false,
  datasetList: {
    qa: [],
    document: [],
  },
  currentPage: 1,
  pageSize: 10,
  total: -1,
  hasMore: false,
  page: 1,
  getDatasetList: async (
    page?: number,
    pageSize?: number,
    type?: "qa" | "document",
  ) => {
    set({ loading: true });
    const currentPage = page ?? get().currentPage;
    const currentPageSize = pageSize ?? get().pageSize;

    try {
      const res = await requestDatasetList({
        page: currentPage,
        pageSize: currentPageSize,
        type: type ?? "qa",
      });

      set((state) => {
        const datasetList = state.datasetList;
        datasetList[type ?? "qa"] = res.data.data;
        return {
          datasetList,
          total: res.data.total || res.data.data.length,
          currentPage,
          pageSize: currentPageSize,
          hasMore: res.data.has_more,
          page: res.data.page,
          loading: false,
        };
      });
    } catch (error) {
      console.error("Failed to fetch dataset list:", error);
      set({ loading: false });
    }
  },
  setCurrentPage: (page: number) => {
    set({ currentPage: page, page });
  },
  setPageSize: (pageSize: number) => {
    set({ pageSize, currentPage: 1, page: 1 });
  },
}));

export const useAdminStore = () => {
  return store(useShallow((state) => state));
};
