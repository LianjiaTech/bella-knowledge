import * as React from "react";
import { Spinner } from "./spinner";

interface LoadingStateProps {
  isLoading: boolean;
  message?: string;
  className?: string;
}

export function LoadingState({
  isLoading,
  message = "加载中...",
  className,
}: LoadingStateProps) {
  if (!isLoading) return null;

  return (
    <div className={`flex justify-center items-center py-4 ${className || ""}`}>
      <Spinner size="md">{message}</Spinner>
    </div>
  );
}
