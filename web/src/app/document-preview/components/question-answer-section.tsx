"use client";

import { Textarea } from "@/components/ui/textarea";
import { Question } from "@/lib/types/qa";

interface QuestionAnswerSectionProps {
  selectedQuestion: Question | null;
  questionInputVal: string;
  answerInputVal: string;
  onQuestionChange: (value: string) => void;
  onAnswerChange: (value: string) => void;
  onBlur: () => void;
}

export function QuestionAnswerSection({
  selectedQuestion,
  questionInputVal,
  answerInputVal,
  onQuestionChange,
  onAnswerChange,
  onBlur,
}: QuestionAnswerSectionProps) {
  if (!selectedQuestion) {
    return null;
  }

  return (
    <>
      <div>
        <div className="text-base font-bold">Query(问题)</div>
        <div className="bg-white rounded-md">
          <Textarea
            value={questionInputVal}
            onChange={(e) => onQuestionChange(e.target.value)}
            rows={2}
            className="mt-4 resize-none scrollbar-hide sm:max-h-20 lg:max-h-30"
            placeholder="请输入问题"
            onBlur={onBlur}
          />
        </div>
      </div>
      <div>
        <div className="text-base font-bold">Answer(答案)</div>
        <div className="bg-white rounded-md">
          <Textarea
            value={answerInputVal}
            onChange={(e) => onAnswerChange(e.target.value)}
            className="mt-4 resize-none scrollbar-hide sm:max-h-20 lg:max-h-30"
            placeholder="请输入回答"
            onBlur={onBlur}
          />
        </div>
      </div>
    </>
  );
}
