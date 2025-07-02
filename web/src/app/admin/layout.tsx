import TopBar from "@/components/top-bar";
import React from "react";
import { Toaster } from "sonner";

const layout = ({ children }: { children: React.ReactNode }) => {
  return (
    <html lang="zh-CN">
      <body>
        <div className="h-screen w-screen">
          <TopBar></TopBar>
          <div className="py-4 px-6 flex flex-col items-start w-screen h-[calc(100vh-4rem)] overflow-y-auto scrollbar-scroll">
            {children}
          </div>
        </div>
        <Toaster />
      </body>
    </html>
  );
};

export default layout;
