"use client";
import React, { useEffect, useState } from "react";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

import TabContent from "./_components/tab-content";
import { useSearchParams } from "next/navigation";

const Page = () => {
  const [value, setValue] = useState("qa");
  const searchParams = useSearchParams();
  const tab = searchParams.get("tab");
  useEffect(() => {
    if (tab) {
      setValue(tab);
    }
  }, [tab]);
  return (
    <>
      <Tabs className="mb-4" value={value} onValueChange={setValue}>
        <TabsList>
          <TabsTrigger value="qa">QA</TabsTrigger>
          <TabsTrigger value="document-parser">文档解析</TabsTrigger>
        </TabsList>
      </Tabs>
      {value === "qa" && <TabContent type="qa" />}
      {value === "document-parser" && <TabContent type="document" />}
    </>
  );
};

export default Page;
