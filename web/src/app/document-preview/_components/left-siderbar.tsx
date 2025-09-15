"use client";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useState, useEffect, useRef } from "react";
import { X, CornerDownLeft, Loader2 } from "lucide-react";
import { Textarea } from "@/components/ui/textarea";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { useSearchParams } from "next/navigation";
import { useDocumentPreviewStore } from "../model";

export function LeftSidebar() {
  const [newQuestionText, setNewQuestionText] = useState("");
  const [open, setOpen] = useState(true);
  const hoverTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const datasetId = useSearchParams().get("dataset_id") || "";
  const {
    questionList,
    selectedQuestion,
    initLoading,
    deleteQuestion,
    addQuestion,
    onChangeSelectedQuestion,
  } = useDocumentPreviewStore();

  const filterQuestionList = questionList.filter((item) =>
    item.question.includes(newQuestionText),
  );
  // 鼠标悬停检测
  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      const threshold = 3;
      if (e.clientX <= threshold && !open) {
        // 鼠标进入左侧区域，打开边栏
        if (hoverTimeoutRef.current) {
          clearTimeout(hoverTimeoutRef.current);
        }
        hoverTimeoutRef.current = setTimeout(() => {
          setOpen(true);
        }, 300);
      } else if (e.clientX > threshold && !open) {
        if (hoverTimeoutRef.current) {
          clearTimeout(hoverTimeoutRef.current);
        }
      }
    };

    document.addEventListener("mousemove", handleMouseMove);

    return () => {
      document.removeEventListener("mousemove", handleMouseMove);
    };
  }, [open, setOpen]);

  const handleAddQuestion = () => {
    if (newQuestionText.trim()) {
      addQuestion({
        question: newQuestionText.trim(),
        answer: "",
        dataset_id: datasetId,
      });
      setNewQuestionText("");
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.nativeEvent.isComposing) {
      return;
    }
    if (e.key === "Enter") {
      e.preventDefault();
      handleAddQuestion();
    }
  };

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetContent
        side="left"
        className="flex flex-col w-100 p-0"
        onMouseEnter={() => {
          // 鼠标进入边栏时，清除关闭定时器
          if (hoverTimeoutRef.current) {
            clearTimeout(hoverTimeoutRef.current);
          }
        }}
        onMouseLeave={() => {
          // 鼠标离开边栏时，延迟关闭
          if (open) {
            hoverTimeoutRef.current = setTimeout(() => {
              setOpen(false);
            }, 300);
          }
        }}
      >
        <SheetHeader className="p-4 border-b">
          <SheetTitle>
            问题列表
            <span className="text-sm text-gray-500 ml-2">
              {questionList.length}条
            </span>
          </SheetTitle>
        </SheetHeader>

        <div className="p-4 pb-30 flex flex-col flex-1 overflow-hidden">
          <div className="relative mb-4">
            <Textarea
              placeholder="请输入，按下回车即可添加"
              value={newQuestionText}
              onChange={(e) => setNewQuestionText(e.target.value)}
              onKeyDown={handleKeyDown}
              rows={2}
              className="pr-10 !resize-none max-h-6 scrollbar-hide"
            />
            <div className="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none">
              <CornerDownLeft size={16} className="text-gray-400" />
            </div>
          </div>

          <ScrollArea className="h-full">
            <div className="h-full overflow-hidden">
              {initLoading ? (
                <div className="flex items-center justify-center h-full">
                  <Loader2 className="size-4 animate-spin" />
                </div>
              ) : filterQuestionList.length === 0 ? (
                <div className="flex items-center justify-center h-full text-gray-500 text-sm">
                  暂无问题
                </div>
              ) : (
                <div>
                  {filterQuestionList.map((question) => {
                    return (
                      <div
                        className={`w-92 h-12 border-b border-gray-200 flex items-center px-3 cursor-pointer justify-between hover:bg-gray-50 transition-colors ${
                          selectedQuestion?.id === question.id
                            ? "bg-blue-100 rounded-md"
                            : ""
                        }`}
                        key={question.id}
                        onClick={() => {
                          onChangeSelectedQuestion(question);
                        }}
                      >
                        <span className="flex-1 truncate text-sm">
                          {question.question}
                        </span>
                        <AlertDialog>
                          <AlertDialogTrigger>
                            <X className="size-4 flex-shrink-0 hover:text-red-500 transition-colors" />
                          </AlertDialogTrigger>
                          <AlertDialogContent>
                            <AlertDialogHeader>
                              <AlertDialogTitle>确定删除吗？</AlertDialogTitle>
                            </AlertDialogHeader>
                            <AlertDialogFooter>
                              <AlertDialogCancel>取消</AlertDialogCancel>
                              <AlertDialogAction
                                onClick={() => deleteQuestion(question)}
                              >
                                确定
                              </AlertDialogAction>
                            </AlertDialogFooter>
                          </AlertDialogContent>
                        </AlertDialog>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </ScrollArea>
        </div>
      </SheetContent>
    </Sheet>
  );
}
