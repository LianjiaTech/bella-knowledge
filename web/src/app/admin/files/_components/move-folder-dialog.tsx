"use client";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { KnowledgeFile } from "@/lib/types/file";
import { findFiles } from "@/request/files";
import { ChevronRight, Folder, Loader2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

type Directory = {
  id: string;
  name: string;
};

type MoveFolderDialogProps = {
  open: boolean;
  file: KnowledgeFile | null;
  currentAncestorId: string;
  spaceCode?: string;
  onOpenChange: (open: boolean) => void;
  onConfirm: (file: KnowledgeFile, ancestorId: string) => Promise<boolean>;
};

const ROOT_DIRECTORY: Directory = {
  id: "",
  name: "我的空间",
};

export function MoveFolderDialog({
  open,
  file,
  currentAncestorId,
  spaceCode,
  onOpenChange,
  onConfirm,
}: MoveFolderDialogProps) {
  const [directoryStack, setDirectoryStack] = useState<Directory[]>([
    ROOT_DIRECTORY,
  ]);
  const [directories, setDirectories] = useState<KnowledgeFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [moving, setMoving] = useState(false);
  const currentDirectory = directoryStack[directoryStack.length - 1];
  const isCurrentParent = currentDirectory.id === currentAncestorId;

  const loadDirectories = useCallback(
    async (ancestorId: string) => {
      setLoading(true);
      const res = await findFiles({
        ancestor_id: ancestorId,
        space_code: spaceCode,
      });
      setDirectories(res.data.filter((item) => item.is_dir));
      setLoading(false);
    },
    [spaceCode],
  );

  useEffect(() => {
    if (!open) {
      return;
    }
    setDirectoryStack([ROOT_DIRECTORY]);
    setMoving(false);
    loadDirectories(ROOT_DIRECTORY.id);
  }, [loadDirectories, open]);

  const invalidMessage = useMemo(() => {
    if (isCurrentParent) {
      return "该目录是当前上级目录，请选择其他目录。";
    }
    return null;
  }, [isCurrentParent]);

  const enterDirectory = async (directory: KnowledgeFile) => {
    if (directory.id === file?.id) {
      return;
    }
    setDirectoryStack((stack) => [
      ...stack,
      { id: directory.id, name: directory.filename },
    ]);
    await loadDirectories(directory.id);
  };

  const jumpDirectory = async (index: number) => {
    const directory = directoryStack[index];
    setDirectoryStack((stack) => stack.slice(0, index + 1));
    await loadDirectories(directory.id);
  };

  const handleConfirm = async () => {
    if (!file || invalidMessage || moving) {
      return;
    }
    setMoving(true);
    const success = await onConfirm(file, currentDirectory.id);
    setMoving(false);
    if (success) {
      onOpenChange(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => !moving && onOpenChange(nextOpen)}
    >
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>移动</DialogTitle>
          <DialogDescription>
            为 &ldquo;{file?.filename}&rdquo; 选择新的上级目录。
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-wrap items-center gap-1 text-sm">
          {directoryStack.map((directory, index) => (
            <div className="flex items-center" key={directory.id || "root"}>
              {index > 0 && (
                <ChevronRight className="text-muted-foreground h-4 w-4" />
              )}
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="h-7 px-2"
                disabled={loading || index === directoryStack.length - 1}
                onClick={() => jumpDirectory(index)}
              >
                {directory.name}
              </Button>
            </div>
          ))}
        </div>

        <ScrollArea className="h-72 rounded-md border">
          <div className="p-2">
            {loading ? (
              <div className="text-muted-foreground flex h-64 items-center justify-center gap-2 text-sm">
                <Loader2 className="h-4 w-4 animate-spin" />
                加载中...
              </div>
            ) : directories.length > 0 ? (
              directories.map((directory) => {
                const isSource = directory.id === file?.id;
                return (
                  <Button
                    type="button"
                    variant="ghost"
                    className="h-auto w-full justify-start gap-2 px-3 py-2"
                    disabled={isSource}
                    key={directory.id}
                    onClick={() => enterDirectory(directory)}
                  >
                    <Folder className="h-4 w-4" />
                    <span className="truncate">{directory.filename}</span>
                    {isSource && (
                      <span className="text-muted-foreground ml-auto text-xs">
                        当前文件夹
                      </span>
                    )}
                  </Button>
                );
              })
            ) : (
              <div className="text-muted-foreground flex h-64 items-center justify-center text-sm">
                当前目录下没有子目录
              </div>
            )}
          </div>
        </ScrollArea>

        <div className="text-sm">
          <span className="text-muted-foreground">目标位置：</span>
          <span>
            {directoryStack.map((directory) => directory.name).join(" / ")}
          </span>
          {invalidMessage && (
            <p className="text-destructive mt-1">{invalidMessage}</p>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            disabled={moving}
            onClick={() => onOpenChange(false)}
          >
            取消
          </Button>
          <Button
            type="button"
            disabled={!file || loading || moving || Boolean(invalidMessage)}
            onClick={handleConfirm}
          >
            {moving ? "移动中..." : "确认移动"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
