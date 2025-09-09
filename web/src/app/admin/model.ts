import { create } from "zustand";
import { useShallow } from "zustand/react/shallow";
import { Dataset } from "@/lib/types/qa";
import {
  requestDatasetList,
  requestUpdateDatasetRemark,
} from "@/request/dataset";
import { toast } from "sonner";

type State = {
  loading: boolean;
  updating: boolean;
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
  getDatasetList: (params: {
    page?: number;
    pageSize?: number;
    type?: "qa" | "document";
  }) => Promise<void>;
  updateDatasetRemark: (datasetId: string, remark: string) => Promise<void>;
  setCurrentPage: (page: number) => void;
  setPageSize: (pageSize: number) => void;
};

const store = create<State & Action>((set, get) => ({
  loading: false,
  updating: false,
  datasetList: {
    qa: [],
    document: [],
  },
  currentPage: 1,
  pageSize: 10,
  total: -1,
  hasMore: false,
  page: 1,
  updateDatasetRemark: async (datasetId: string, remark: string) => {
    if (get().updating) {
      return;
    }
    const dataset = get().datasetList.qa.find(
      (item) => item.dataset_id === datasetId,
    );
    if (!dataset) {
      toast.error("数据集不存在");
      return;
    }
    if (dataset.remark === remark) {
      return;
    }
    set({ updating: true });
    const res = await requestUpdateDatasetRemark(datasetId, remark);
    if (res) {
      set((state) => {
        const datasetList = state.datasetList;
        datasetList.qa = datasetList.qa.map((item) => {
          if (item.dataset_id === datasetId) {
            return {
              ...item,
              remark,
            };
          }
          return item;
        });
        return {
          datasetList,
          updating: false,
        };
      });
      toast.success("更新成功");
    } else {
      toast.error(res || "更新失败");
    }
  },
  getDatasetList: async ({ page, pageSize, type }) => {
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
