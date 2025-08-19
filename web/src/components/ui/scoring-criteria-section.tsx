"use client";

import * as React from "react";
import { ChevronDown, ChevronUp, ChevronRight, Target } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

interface ScoringCriteriaSectionProps {
  scoring_criteria?: string;
  onScoringCriteriaChange: (value: string) => void;
  onBlur: () => void;
  className?: string;
}

export function ScoringCriteriaSection({
  scoring_criteria,
  onScoringCriteriaChange,
  onBlur,
  className,
}: ScoringCriteriaSectionProps) {
  const [isExpanded, setIsExpanded] = React.useState(false);

  const hasContent = scoring_criteria && scoring_criteria.trim().length > 0;

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
          !hasContent && "hover:bg-orange-50/50 border-l-2 border-transparent hover:border-l-orange-200 pl-2"
        )}
        onClick={handleToggleExpand}
        title={isExpanded ? "收起得分要点" : "展开得分要点"}
      >
        <Target className="mr-2 h-4 w-4" />
        <span className="flex-1 font-bold">得分要点<span className="text-xs font-normal opacity-60 ml-1">（可选）</span></span>
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
          value={scoring_criteria || ""}
          onChange={(e) => onScoringCriteriaChange(e.target.value)}
          onBlur={handleBlur}
          className="resize-none text-sm"
          placeholder="请输入评分依据/得分要点..."
          rows={3}
          autoFocus
        />
      )}
    </div>
  );
}