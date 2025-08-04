"use client";

import * as React from "react";
import { ChevronDown, ChevronUp, ChevronRight, Lightbulb } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

interface ReasoningSectionProps {
  reasoning?: string;
  onReasoningChange: (value: string) => void;
  onBlur: (tags?: string[]) => void;
  className?: string;
}

export function ReasoningSection({
  reasoning,
  onReasoningChange,
  onBlur,
  className,
}: ReasoningSectionProps) {
  const [isExpanded, setIsExpanded] = React.useState(false);

  const hasContent = reasoning && reasoning.trim().length > 0;

  const handleToggleExpand = () => {
    setIsExpanded(!isExpanded);
  };

  const handleBlur = () => {
    onBlur();
  };

  return (
    <div className={cn("space-y-2", className)}>
      <Button
        variant="ghost"
        size="sm"
        className={cn(
          "w-full justify-start text-left font-normal text-muted-foreground hover:text-foreground",
          "px-0 py-1 h-auto group",
          !hasContent && "hover:bg-blue-50/50 border-l-2 border-transparent hover:border-l-blue-200 pl-2"
        )}
        onClick={handleToggleExpand}
        title={isExpanded ? "收起解题思路" : "展开解题思路"}
      >
        <Lightbulb className="mr-2 h-4 w-4" />
        <span className="flex-1 font-bold">解题思路<span className="text-xs font-normal opacity-60 ml-1">（可选）</span></span>
        {!hasContent && !isExpanded && (
          <span className="text-xs opacity-70 mr-2">点击添加</span>
        )}
        <div className="flex items-center space-x-1">
          {isExpanded ? (
            <ChevronUp className="h-4 w-4" />
          ) : hasContent ? (
            <ChevronDown className="h-4 w-4" />
          ) : (
            <ChevronRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
          )}
        </div>
      </Button>
      
      {isExpanded && (
        <Textarea
          value={reasoning || ""}
          onChange={(e) => onReasoningChange(e.target.value)}
          onBlur={handleBlur}
          className="resize-none text-sm"
          placeholder="请输入解题思路..."
          rows={3}
          autoFocus
        />
      )}
    </div>
  );
}