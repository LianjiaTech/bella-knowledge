"use client";

import * as React from "react";
import { Textarea } from "@/components/ui/textarea";
import { TagSelector } from "@/components/ui/tag-selector";
import { ReasoningSection } from "@/components/ui/reasoning-section";
import { useDocumentPreviewStore } from "../model";

export function QuestionAnswerSection() {
  const {
    selectedQuestion,
    questionInputVal,
    answerInputVal,
    selectedTags,
    reasoningText,
    availableTags,
    onChangeQuestionInputVal,
    onChangeAnswerInputVal,
    onChangeSelectedTags,
    onChangeReasoningText,
    getTagsList,
    updateQuestion,
  } = useDocumentPreviewStore();

  if (!selectedQuestion) {
    return null;
  }

  const onBlur = () => {
    updateQuestion({
      ...selectedQuestion,
      question: questionInputVal,
      answer: answerInputVal,
      tags: selectedTags,
      reasoning: reasoningText,
    });
  };

  return (
    <>
      <div>
        <div className="text-base font-bold">Query(问题)</div>
        <div className="bg-white rounded-md">
          <Textarea
            value={questionInputVal}
            onChange={(e) => onChangeQuestionInputVal(e.target.value)}
            rows={2}
            className="mt-4 resize-none scrollbar-hide sm:max-h-20 lg:max-h-30"
            placeholder="请输入问题"
            onBlur={onBlur}
          />
        </div>
        
        {/* Tag selector for questions */}
        <div className="mt-3">
          <TagSelector
            selectedTags={selectedTags}
            availableTags={availableTags}
            onTagsChange={onChangeSelectedTags}
            onRefreshTags={getTagsList}
            onBlur={onBlur}
            placeholder="添加标签"
          />
        </div>
      </div>
      
      <div>
        <div className="text-base font-bold">Answer(答案)</div>
        <div className="bg-white rounded-md">
          <Textarea
            value={answerInputVal}
            onChange={(e) => onChangeAnswerInputVal(e.target.value)}
            className="mt-4 resize-none scrollbar-hide sm:max-h-20 lg:max-h-30"
            placeholder="请输入回答"
            onBlur={onBlur}
          />
        </div>
        
        {/* Reasoning section for answers */}
        <div className="mt-3">
          <ReasoningSection 
            reasoning={reasoningText}
            onReasoningChange={onChangeReasoningText}
            onBlur={onBlur}
          />
        </div>
      </div>

    </>
  );
}
