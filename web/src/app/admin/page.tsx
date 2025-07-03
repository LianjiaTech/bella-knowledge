"use client";
import { useUserStore } from "@/store/user";
import React, { useEffect, useMemo, useState } from "react";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getColumns } from "./columns";
import { DataTable } from "./data-table";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { CreateDatasetForm } from "./create-dataset-form";
import { Button } from "@/components/ui/button";
import { useAdminStore } from "./model";
import { Spinner } from "@/components/ui/spinner";

const Page = () => {
  const { getDatasetList, datasetList, loading } = useAdminStore();
  const { currentWorkspace } = useUserStore();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (currentWorkspace) {
      getDatasetList();
    }
  }, [currentWorkspace, getDatasetList]);

  const columns = useMemo(() => {
    return getColumns(() => {
      getDatasetList();
    });
  }, [getDatasetList]);

  return (
    <>
      <Tabs className="mb-4" value="dataset">
        <TabsList>
          <TabsTrigger value="dataset">QA</TabsTrigger>
        </TabsList>
      </Tabs>
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetTrigger asChild>
          <Button>创建数据集</Button>
        </SheetTrigger>
        <SheetContent className="w-[800px] overflow-y-auto pb-4">
          <SheetHeader>
            <SheetTitle>创建数据集</SheetTitle>
          </SheetHeader>
          <CreateDatasetForm
            onSuccess={() => {
              getDatasetList();
              setOpen(false);
            }}
          />
        </SheetContent>
      </Sheet>
      <div className="flex-1 mt-10 self-stretch">
        {loading ? (
          <div className="flex justify-center items-center h-64">
            <Spinner size="lg">加载中...</Spinner>
          </div>
        ) : (
          <DataTable columns={columns} data={datasetList} />
        )}
      </div>
    </>
  );
};

export default Page;
