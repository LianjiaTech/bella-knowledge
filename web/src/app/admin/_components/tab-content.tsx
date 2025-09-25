"use client";
import { DataTable } from "./data-table";
import { Spinner } from "@/components/ui/spinner";
import { useEffect, useMemo, useState } from "react";
import { getColumns } from "./columns";
import { useUserStore } from "@/store/user";
import { useAdminStore } from "../model";
import CreateDatasetSheet from "../_components/create-dataset-sheet";
interface TabContentProps {
  type: "qa" | "document";
}

const TabContent = ({ type }: TabContentProps) => {
  const { getDatasetList, updateDatasetRemark, datasetList, loading } =
    useAdminStore();
  const { currentWorkspace } = useUserStore();

  useEffect(() => {
    if (currentWorkspace) {
      getDatasetList({ page: 1, type });
    }
  }, [currentWorkspace, getDatasetList, type]);

  const columns = useMemo(() => {
    return getColumns(
      async (datasetId, remark) => {
        await updateDatasetRemark(datasetId, remark);
      },
      () => {
        getDatasetList({ type });
      },
      type,
    );
  }, [getDatasetList, type, updateDatasetRemark]);
  const [open, setOpen] = useState(false);

  return (
    <>
      <CreateDatasetSheet open={open} onOpenChange={setOpen} type={type} />
      <div className="flex-1 mt-10 self-stretch">
        {loading ? (
          <div className="flex justify-center items-center h-64">
            <Spinner size="lg">加载中...</Spinner>
          </div>
        ) : (
          <DataTable columns={columns} data={datasetList[type]} />
        )}
      </div>
    </>
  );
};

export default TabContent;
