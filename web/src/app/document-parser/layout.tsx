import React from "react";
import TopBar from "./_components/top-bar";
import { Toaster } from "sonner";
import { Suspense } from "react";

const DocumentParserLayout = ({ children }: { children: React.ReactNode }) => {
  return (
    <Suspense>
      <div className="flex flex-col h-screen w-screen">
        <TopBar lastEditTime={0} />
        <div className="flex flex-1 overflow-hidden">{children}</div>
        <Toaster />
      </div>
    </Suspense>
  );
};

export default DocumentParserLayout;
