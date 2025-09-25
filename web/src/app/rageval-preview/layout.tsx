"use client";
import React from "react";
import { Toaster } from "@/components/ui/sonner";

export default function DocumentPreviewLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="relative h-screen">
      <div>{children}</div>
      <Toaster />
    </div>
  );
}
