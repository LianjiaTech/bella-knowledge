export type FileProgress = {
  id?: number;
  fileId?: string;
  name?: string | null;
  status?: string | null;
  message?: string | null;
  percent?: number | null;
};

export type DisplayFileProgress = FileProgress & {
  displayName: string;
  displayStatus: string;
  percentValue: number | null;
  terminal: boolean;
};

const names: Record<string, string> = {
  document_parser: "文档解析",
  file_indexing: "文件索引",
};

const terminalStatuses = new Set([
  "document_parse_finish",
  "document_parse_failed",
  "finish",
  "finished",
  "complete",
  "completed",
  "success",
  "succeeded",
  "failed",
  "failure",
  "error",
]);

export function toDisplayFileProgress(progress: FileProgress): DisplayFileProgress {
  const rawName = progress.name?.trim() || "未知后处理";
  const rawStatus = progress.status?.trim() || "未知状态";
  const numericPercent = progress.percent === null || progress.percent === undefined
    ? Number.NaN
    : typeof progress.percent === "number"
      ? progress.percent
      : Number(progress.percent);
  const percentValue = Number.isFinite(numericPercent)
    ? Math.min(100, Math.max(0, numericPercent))
    : null;
  const status = rawStatus.toLowerCase();
  let displayStatus = "未知状态";
  if (status === "pending" || status === "waiting" || status === "document_parse_pending") {
    displayStatus = "尚未开始";
  } else if (["processing", "running", "in_progress", "document_parse_processing"].includes(status)) {
    displayStatus = "处理中";
  } else if (terminalStatuses.has(status)) {
    displayStatus = status.includes("fail") || status === "failure" || status === "error" ? "失败" : "已完成";
  } else {
    displayStatus = rawStatus;
  }
  return {
    ...progress,
    displayName: names[rawName] ? `${names[rawName]}（${rawName}）` : `${rawName}（未知后处理）`,
    displayStatus,
    percentValue,
    terminal: terminalStatuses.has(status),
  };
}

export function hasActiveFileProgress(progresses: FileProgress[]) {
  return progresses.some((progress) => !toDisplayFileProgress(progress).terminal);
}
