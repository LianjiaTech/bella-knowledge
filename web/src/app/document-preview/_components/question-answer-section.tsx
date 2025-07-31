"use client";

import { Textarea } from "@/components/ui/textarea";
import { useDocumentPreviewStore } from "../model";

export function QuestionAnswerSection() {
  const {
    selectedQuestion,
    questionInputVal,
    answerInputVal,
    onChangeQuestionInputVal,
    onChangeAnswerInputVal,
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
      </div>
    </>
  );
}
