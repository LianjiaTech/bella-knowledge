import { create } from "zustand";
import { useShallow } from "zustand/react/shallow";
import { Dataset } from "./columns";
import { webRequest } from "@/lib/request/web";

type State = {
  loading: boolean;
  datasetList: Dataset[];
  currentPage: number;
  pageSize: number;
  total: number;
  hasMore: boolean;
  page: number;
};

type Action = {
  getDatasetList: (page?: number, pageSize?: number) => Promise<void>;
  setCurrentPage: (page: number) => void;
  setPageSize: (pageSize: number) => void;
};

const store = create<State & Action>((set, get) => ({
  loading: false,
  datasetList: [],
  currentPage: 1,
  pageSize: 10,
  total: -1,
  hasMore: false,
  page: 1,
  getDatasetList: async (page?: number, pageSize?: number) => {
    set({ loading: true });
    const currentPage = page ?? get().currentPage;
    const currentPageSize = pageSize ?? get().pageSize;

    try {
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
          page: currentPage.toString(),
          page_size: currentPageSize.toString(),
        },
      });

      set({
        datasetList: res.data.data,
        total: res.data.total || res.data.data.length,
        currentPage,
        pageSize: currentPageSize,
        hasMore: res.data.has_more,
        page: res.data.page,
        loading: false,
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
