import TopBar from "@/components/top-bar";
import React, { Suspense } from "react";
import { Toaster } from "sonner";

const layout = ({ children }: { children: React.ReactNode }) => {
  return (
    <Suspense>
      <div className="flex flex-col h-screen w-screen">
        <TopBar></TopBar>
        <div className="py-4 px-6 flex flex-col w-screen overflow-y-auto scrollbar-scroll">
          {children}
        </div>
      </div>
      <Toaster />
    </Suspense>
  );
};

export default layout;
