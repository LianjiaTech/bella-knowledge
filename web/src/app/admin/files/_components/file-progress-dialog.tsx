"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { getFileProgresses } from "@/request/files";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Progress } from "@/components/ui/progress";
import { FileProgress, hasActiveFileProgress, toDisplayFileProgress } from "./file-progress";

type Props = { file: { id: string; filename: string } | null; onClose: () => void };

export function FileProgressDialog({ file, onClose }: Props) {
  const [progresses, setProgresses] = useState<FileProgress[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [timedOut, setTimedOut] = useState(false);
  const generation = useRef(0);

  const load = useCallback(async (currentGeneration: number) => {
    if (!file) return undefined;
    setLoading(true);
    const result = await getFileProgresses(file.id);
    if (generation.current !== currentGeneration) return undefined;
    setLoading(false);
    if (result.error) {
      setError(result.error);
      return result;
    }
    setError(null);
    setProgresses(result.data);
    return result;
  }, [file]);

  useEffect(() => {
    if (!file) return;
    const currentGeneration = ++generation.current;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const startedAt = Date.now();
    setProgresses([]);
    setError(null);
    setTimedOut(false);

    const poll = async () => {
      if (cancelled) return;
      const result = await load(currentGeneration);
      if (cancelled || generation.current !== currentGeneration || !result) return;
      const active = hasActiveFileProgress(result.data);
      const emptyWaiting = result.data.length === 0 && Date.now() - startedAt < 30000;
      if (active || emptyWaiting || result.error) {
        if (Date.now() - startedAt >= 30000 && result.data.length === 0) {
          setTimedOut(true);
          return;
        }
        timer = setTimeout(poll, result.error ? 6000 : active ? 3000 : 3000);
      }
    };
    poll();
    return () => {
      cancelled = true;
      generation.current++;
      if (timer) clearTimeout(timer);
    };
  }, [file, load]);

  const close = () => {
    generation.current++;
    setProgresses([]);
    setError(null);
    setTimedOut(false);
    onClose();
  };

  return (
    <Dialog open={!!file} onOpenChange={(open) => !open && close()}>
      <DialogContent>
        <DialogHeader><DialogTitle>解析进度：{file?.filename}</DialogTitle></DialogHeader>
        {loading && progresses.length === 0 && <p className="text-sm text-muted-foreground">加载中...</p>}
        {error && <p className="text-sm text-red-600">查询失败：{error}</p>}
        {!loading && !error && progresses.length === 0 && (
          <p className="text-sm text-muted-foreground">{timedOut ? "暂未产生进度数据" : "暂无进度数据"}</p>
        )}
        <div className="space-y-4">
          {progresses.map((progress, index) => {
            const item = toDisplayFileProgress(progress);
            return <div key={progress.id ?? `${progress.name}-${index}`} className="space-y-2">
              <div className="flex justify-between gap-4 text-sm"><span>{item.displayName}</span><span>{item.percentValue === null ? "—" : `${item.percentValue}%`} · {item.displayStatus}</span></div>
              <Progress value={item.percentValue ?? 0} />
              {item.message && <p className="text-sm text-muted-foreground break-words">{item.message}</p>}
            </div>;
          })}
        </div>
      </DialogContent>
    </Dialog>
  );
}
