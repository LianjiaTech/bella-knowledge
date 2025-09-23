"use client";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useState, useEffect, useRef } from "react";
import { CornerDownLeft } from "lucide-react";
import { Textarea } from "@/components/ui/textarea";
import { ScrollArea } from "@/components/ui/scroll-area";
import { RagevalData } from "@/lib/types/rageval";

export function LeftSidebar({
  data,
  selectedRagevalData,
  setSelectedRagevalData,
}: {
  data: RagevalData[];
  selectedRagevalData: RagevalData | null;
  setSelectedRagevalData: (question: RagevalData | null) => void;
}) {
  const [questionText, setQuestionText] = useState("");
  const [open, setOpen] = useState(true);
  const hoverTimeoutRef = useRef<NodeJS.Timeout | null>(null);

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

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.nativeEvent.isComposing) {
      return;
    }
    if (e.key === "Enter") {
      e.preventDefault();
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
            <span className="text-sm text-gray-500 ml-2"></span>
          </SheetTitle>
        </SheetHeader>

        <div className="p-4 pb-30 flex flex-col flex-1 overflow-hidden">
          <div className="relative mb-4">
            <Textarea
              placeholder="输入后可根据问题内容进行"
              value={questionText}
              onChange={(e) => setQuestionText(e.target.value)}
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
              {data
                .filter((item) => item.question.includes(questionText))
                .map((item, index) => (
                  <div
                    className={`w-92 h-12 border-b border-gray-200 flex items-center px-3 cursor-pointer justify-between hover:bg-gray-50 transition-colors ${
                      selectedRagevalData?.question === item.question
                        ? "bg-blue-100 rounded-md"
                        : ""
                    }`}
                    key={index}
                    onClick={() => {
                      setSelectedRagevalData(item);
                    }}
                  >
                    <span className="flex-1 truncate text-sm">
                      {item.question}
                    </span>
                  </div>
                ))}
            </div>
          </ScrollArea>
        </div>
      </SheetContent>
    </Sheet>
  );
}
