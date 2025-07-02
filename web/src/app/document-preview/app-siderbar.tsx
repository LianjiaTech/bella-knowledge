"use client";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useState } from "react";
import { X, CornerDownLeft } from "lucide-react";
import { Question, QuestionList } from "@/lib/types/qa";
import { Textarea } from "@/components/ui/textarea";
import { ScrollArea } from "@/components/ui/scroll-area";

interface AppSidebarProps {
  questionList: QuestionList;
  selectedQuestion: Question | null;
  datasetId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onChangeSelectedQuestion: (question: Question) => void;
  onDeleteQuestion: (question: Question) => void;
  onAddQuestion: (params: {
    question: string;
    answer: string;
    dataset_id: string;
  }) => void;
}

export function AppSidebar({
  questionList,
  selectedQuestion,
  datasetId,
  open,
  onOpenChange,
  onChangeSelectedQuestion,
  onDeleteQuestion,
  onAddQuestion,
}: AppSidebarProps) {
  const [newQuestionText, setNewQuestionText] = useState("");

  const handleAddQuestion = () => {
    if (newQuestionText.trim()) {
      onAddQuestion({
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
      handleAddQuestion();
      setNewQuestionText("");
    }
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="left" className="w-100 p-0">
        <SheetHeader className="p-4 border-b">
          <SheetTitle>问题列表</SheetTitle>
        </SheetHeader>

        <div className="p-4 flex flex-col h-full">
          {/* 新增问题输入框 */}
          <div className="mb-4 space-y-2 flex-1">
            <div className="flex gap-2">
              <div className="relative flex-1">
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
            </div>
          </div>

          <ScrollArea className="h-full">
            <div className="flex-1">
              {questionList.length === 0 ? (
                <div className="flex items-center justify-center h-full">
                  <span className="text-gray-500">暂无问题</span>
                </div>
              ) : (
                <div className="space-y-1">
                  {questionList.map((question) => {
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
                        <X
                          className="size-4 flex-shrink-0 hover:text-red-500 transition-colors"
                          onClick={(e) => {
                            e.stopPropagation();
                            onDeleteQuestion(question);
                          }}
                        />
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
