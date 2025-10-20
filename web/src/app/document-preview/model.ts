import { create } from "zustand";
import {
  QaReference,
  QaReferenceList,
  Question,
  QuestionList,
} from "@/lib/types/qa";
import { useShallow } from "zustand/react/shallow";
import { webRequest } from "@/lib/request/web";
import { toast } from "sonner";
import { KnowledgeFile } from "@/lib/types/file";
import { getFileList } from "@/request/files";
import { requestGetDatasetQuestionList } from "@/request/dataset";
import { requestCreateQaReference, requestUpdateQaReferencePrimary } from "@/request/qa-reference";
import { requestTagsList, requestCreateTag, Tag } from "@/request/tags";

type State = {
  questionInputVal: string;
  answerInputVal: string;
  selectedTags: string[];
  reasoningText: string;
  scoringCriteriaText: string;
  availableTags: Tag[];
  selectedQuestion: Question | null;
  questionList: QuestionList;
  qaReferenceList: QaReferenceList;
  fileList: KnowledgeFile[];
  referenceFileList: KnowledgeFile[];
  selectFileId: string;
  lastEditTime: number;
  initLoading: boolean;
};
type Action = {
  onChangeQuestionInputVal: (val: string) => void;
  onChangeAnswerInputVal: (val: string) => void;
  onChangeSelectedTags: (tags: string[]) => void;
  onChangeReasoningText: (reasoning: string) => void;
  onChangeScoringCriteriaText: (scoring_criteria: string) => void;
  onChangeSelectedQuestion: (question: Question) => void;
  addQuestion: (body: {
    dataset_id: string;
    question: string;
    answer: string;
    tags?: string[];
    reasoning?: string;
    scoring_criteria?: string;
  }) => void;
  deleteQuestion: (question: Question) => void;
  updateQuestion: (
    question: Question & {
      tags?: string[];
      reasoning?: string;
      scoring_criteria?: string;
    },
  ) => void;
  getQuestionList: (dataset_id: string) => void;
  addQuestionReference: (params: {
    dataset_id: string;
    item_id: string;
    file_id: string;
    path: number[];
    snippet: string;
  }) => Promise<void>;
  deleteQuestionReference: (body: {
    dataset_id: string;
    reference_id: number;
  }) => void;
  updateReferencePrimary: (body: {
    dataset_id: string;
    reference_id: string;
    primary: number;
  }) => Promise<void>;
  getReferenceList: (dataset_id: string, item_id: string) => void;
  getFileList: () => void;
  addReferenceFile: (fileId: string) => void;
  addUploadFile: (file: KnowledgeFile) => void;
  initPage: (dataset_id: string) => Promise<void>;
  initReferenceFileList: (dataset_id: string) => Promise<void>;
  onFileSelect: (fileId: string) => void;
  getTagsList: () => Promise<void>;
  createTag: (name: string) => void;
  updateTag: (id: number, name: string) => void;
  deleteTag: (id: number) => void;
  clear: () => void;
};

const store = create<State & Action>((set, get) => ({
  questionInputVal: "",
  answerInputVal: "",
  selectedTags: [],
  reasoningText: "",
  scoringCriteriaText: "",
  availableTags: [],
  questionList: [],
  qaReferenceList: [],
  selectedQuestion: null,
  fileList: [],
  referenceFileList: [],
  selectFileId: "",
  lastEditTime: 0,
  initLoading: false,
  getQuestionList: async (dataset_id: string) => {
    const res = await requestGetDatasetQuestionList(dataset_id);
    if (res.code === 200) {
      set({ questionList: res.data });
    }
  },
  addQuestion: async (question) => {
    const { questionList } = get();
    const res = await webRequest<Question>({
      path: "/api/qa",
      method: "POST",
      body: question,
    });
    if (res.code === 200) {
      set({
        questionList: [res.data, ...questionList],
        selectedQuestion: res.data,
        questionInputVal: res.data.question,
        answerInputVal: res.data.answer,
        selectedTags: res.data.tags || [],
        reasoningText: res.data.reasoning || "",
        scoringCriteriaText: res.data.scoring_criteria || "",
        lastEditTime: Date.now(),
      });
      toast.success("添加成功");
    }
  },
  deleteQuestion: async ({ dataset_id, item_id }) => {
    const { questionList, selectedQuestion } = get();
    const res = await webRequest({
      path: `/api/qa`,
      method: "DELETE",
      body: {
        item_id: item_id,
        dataset_id: dataset_id,
      },
    });
    if (res.code === 200) {
      if (selectedQuestion?.item_id === item_id) {
        set({
          selectedQuestion: null,
          questionInputVal: "",
          answerInputVal: "",
          reasoningText: "",
          scoringCriteriaText: "",
        });
      }
      set({
        questionList: questionList.filter(
          (question) => question.item_id !== item_id,
        ),
        lastEditTime: Date.now(),
      });
      toast.success("删除成功");
    }
  },
  updateQuestion: async ({
    dataset_id,
    item_id,
    question,
    answer,
    tags,
    reasoning,
    scoring_criteria,
  }) => {
    const { questionList } = get();
    const currentQuestion = questionList.find(
      (question) => question.item_id === item_id,
    );
    if (
      currentQuestion?.question === question &&
      currentQuestion?.answer === answer &&
      JSON.stringify(currentQuestion?.tags) === JSON.stringify(tags) &&
      currentQuestion?.reasoning === reasoning &&
      currentQuestion?.scoring_criteria === scoring_criteria
    ) {
      return;
    }
    const res = await webRequest<Question>({
      path: "/api/qa",
      method: "PUT",
      body: {
        item_id: item_id,
        dataset_id: dataset_id,
        question: question,
        answer: answer,
        tags: tags,
        reasoning: reasoning,
        scoring_criteria: scoring_criteria,
      },
    });
    if (res.code === 200) {
      set({
        questionList: questionList.map((question) =>
          question.item_id === item_id ? res.data : question,
        ),
        selectedQuestion: res.data,
        questionInputVal: res.data.question,
        answerInputVal: res.data.answer,
        selectedTags: res.data.tags || [],
        reasoningText: res.data.reasoning || "",
        scoringCriteriaText: res.data.scoring_criteria || "",
        lastEditTime: Date.now(),
      });
      toast.success("更新成功");
    }
  },
  addQuestionReference: async (data) => {
    const { qaReferenceList } = get();
    const qaReference = qaReferenceList.find(
      (qaReference) => qaReference.item_id === data.item_id,
    );
    if (
      qaReference?.references.find(
        (r) =>
          r.file_id === data.file_id &&
          r.path.length <= data.path.length &&
          r.path.every((v, i) => Number(v) === Number(data.path[i])),
      )
    ) {
      toast.error("当前文件的该节点已存在标注");
      return;
    }
    const {
      result,
      data: resData,
      message,
    } = await requestCreateQaReference({
      ...data,
      children_references: qaReference?.references
        .filter(
          (r) =>
            r.path.length > data.path.length &&
            data.path.every((v, i) => Number(v) === Number(r.path[i])),
        )
        .map((r) => r.reference_id),
    });

    if (result) {
      const newReference = {
        file_id: data.file_id,
        path: data.path,
        reference_id: resData!.reference_id,
        snippet: data.snippet,
      };

      if (qaReference) {
        set({
          qaReferenceList: qaReferenceList.map((qaReference) =>
            qaReference.item_id === data.item_id
              ? {
                  ...qaReference,
                  references: [...qaReference.references, newReference].filter(
                    (r) =>
                      r.path.length <= newReference.path.length ||
                      !newReference.path.every(
                        (v, i) => Number(v) === Number(r.path[i]),
                      ),
                  ),
                }
              : qaReference,
          ),
          lastEditTime: Date.now(),
        });
        toast.success("添加成功，并且自动合并子节点");
      } else {
        set({
          qaReferenceList: [
            ...qaReferenceList,
            {
              item_id: data.item_id,
              references: [newReference],
            },
          ],
          lastEditTime: Date.now(),
        });
      }
    } else {
      toast.error(message);
    }
  },
  deleteQuestionReference: async (body: {
    dataset_id: string;
    reference_id: number;
  }) => {
    const { qaReferenceList } = get();
    const res = await webRequest({
      path: "/api/qa-reference",
      method: "DELETE",
      body,
    });
    if (res.code === 200) {
      set({
        qaReferenceList: qaReferenceList.map((qaReference) => ({
          ...qaReference,
          references: qaReference.references.filter(
            (r) => r.reference_id !== body.reference_id,
          ),
        })),
        lastEditTime: Date.now(),
      });
      toast.success("删除成功");
    }
  },
  updateReferencePrimary: async (body: {
    dataset_id: string;
    reference_id: string;
    primary: number;
  }) => {
    const { qaReferenceList } = get();
    const res = await requestUpdateQaReferencePrimary(body);
    if (res.code === 200) {
      set({
        qaReferenceList: qaReferenceList.map((qaReference) => ({
          ...qaReference,
          references: qaReference.references.map((r) =>
            r.reference_id.toString() === body.reference_id
              ? { ...r, primary: body.primary }
              : r
          ),
        })),
        lastEditTime: Date.now(),
      });
      toast.success(body.primary === 1 ? "已标记为关键知识" : "已取消关键知识标记");
    } else {
      toast.error("更新失败");
    }
  },
  getReferenceList: async (dataset_id: string, item_id: string) => {
    const { qaReferenceList } = get();
    const qaReference = qaReferenceList.find(
      (qaReference) => qaReference.item_id === item_id,
    );
    if (qaReference) {
      return;
    }
    const res = await webRequest<QaReference>({
      path: "/api/qa-reference/list",
      method: "GET",
      query: { dataset_id: dataset_id, item_id: item_id },
    });
    if (res.code === 200) {
      set({
        qaReferenceList: [...qaReferenceList, res.data],
      });
    }
  },
  onChangeSelectedQuestion: (question) => {
    const { selectedQuestion } = get();
    if (selectedQuestion?.item_id === question.item_id) {
      set({ selectedQuestion: null });
    } else {
      set({
        selectedQuestion: question,
        questionInputVal: question.question,
        answerInputVal: question.answer,
        selectedTags: question.tags || [],
        reasoningText: question.reasoning || "",
        scoringCriteriaText: question.scoring_criteria || "",
      });
      const { getReferenceList } = get();
      getReferenceList(question.dataset_id, question.item_id.toString());
    }
  },
  getFileList: async () => {
    const res = await getFileList();
    set({ fileList: res });
  },
  addReferenceFile: async (fileId: string) => {
    const { referenceFileList, fileList } = get();
    if (referenceFileList.find((file) => file.id === fileId)) {
      toast("已添加", {
        position: "top-center",
      });
      return;
    }
    const file = fileList.find((file) => file.id === fileId);
    if (file) {
      set({
        referenceFileList: [...referenceFileList, file],
      });
    }
  },
  addUploadFile: (file: KnowledgeFile) => {
    const { fileList } = get();
    set({
      fileList: [file, ...fileList],
    });
  },
  initPage: async (dataset_id: string) => {
    const { initLoading } = get();
    if (initLoading) {
      return;
    }
    set({ initLoading: true });

    await Promise.all([
      get().getQuestionList(dataset_id),
      get().getFileList(),
      get().getTagsList(),
    ]);
    set({ initLoading: false });
  },
  initReferenceFileList: async (dataset_id: string) => {
    const { questionList } = get();
    if (questionList.length > 0) {
      // 修改批处理大小为100
      const batchSize = 100;
      const batches = [];
      for (let i = 0; i < questionList.length; i += batchSize) {
        batches.push(questionList.slice(i, i + batchSize));
      }

      const singleReq = async (itemIds: string[]) => {
        const res = await webRequest<string[]>({
          path: "/api/reference-files",
          method: "POST",
          body: {
            dataset_id,
            item_ids: itemIds,
          },
        });
        if (res.code === 200 && res.data?.length > 0) {
          return res.data;
        }
        return [];
      };

      const executeBatchesWithLimit = async (
        batches: Question[][],
        limit: number,
      ) => {
        const results: string[][] = [];

        for (let i = 0; i < batches.length; i += limit) {
          const currentBatch = batches.slice(i, i + limit);
          const promises = currentBatch.map((batch) =>
            singleReq(batch.map((item: Question) => item.item_id.toString())),
          );

          const batchResults = await Promise.all(promises);
          results.push(...batchResults);
        }

        return results;
      };

      const allResults = await executeBatchesWithLimit(batches, 2);

      // 合并所有结果并更新状态
      const allFileIds = allResults.flat();
      if (allFileIds.length > 0) {
        set((state) => ({
          referenceFileList: [
            ...state.referenceFileList,
            ...state.fileList.filter((file) => allFileIds.includes(file.id)),
          ],
        }));
      }
      set((state) => ({
        selectFileId:
          state.referenceFileList.length > 0
            ? state.referenceFileList[0].id
            : "",
      }));
    }
  },
  onChangeQuestionInputVal: (val: string) => {
    set({ questionInputVal: val });
  },
  onChangeAnswerInputVal: (val: string) => {
    set({ answerInputVal: val });
  },
  onChangeSelectedTags: (tags: string[]) => {
    set({ selectedTags: tags });
  },
  onChangeReasoningText: (reasoning: string) => {
    set({ reasoningText: reasoning });
  },
  onChangeScoringCriteriaText: (scoringCriteria: string) => {
    set({ scoringCriteriaText: scoringCriteria });
  },
  getTagsList: async () => {
    try {
      const res = await requestTagsList();
      if (res.code === 200) {
        set({ availableTags: res.data });
      }
    } catch (error) {
      console.error("获取标签列表失败:", error);
    }
  },
  createTag: async (name: string) => {
    try {
      const res = await requestCreateTag(name);
      if (res.code === 200 && res.data) {
        // 直接添加到现有标签列表中，避免重新请求
        const { availableTags } = get();
        set({
          availableTags: [...availableTags, res.data],
        });
        toast.success(`标签 "${name}" 创建成功`);
      } else {
        toast.error(res.message || "创建标签失败");
      }
    } catch (error) {
      toast.error("创建标签失败");
      console.error("创建标签失败:", error);
    }
  },
  updateTag: (id: number, name: string) => {
    const { availableTags, selectedTags } = get();
    const oldTag = availableTags.find((tag) => tag.id === id);
    if (oldTag) {
      set({
        availableTags: availableTags.map((tag) =>
          tag.id === id ? { ...tag, name } : tag,
        ),
        selectedTags: selectedTags.map((tag) =>
          tag === oldTag.name ? name : tag,
        ),
      });
      toast.success("标签更新成功");
    }
  },
  deleteTag: (id: number) => {
    const { availableTags, selectedTags } = get();
    const tagToDelete = availableTags.find((tag) => tag.id === id);
    if (tagToDelete) {
      set({
        availableTags: availableTags.filter((tag) => tag.id !== id),
        selectedTags: selectedTags.filter((tag) => tag !== tagToDelete.name),
      });
      toast.success("标签删除成功");
    }
  },
  onFileSelect: (fileId: string) => {
    set({ selectFileId: fileId });
  },
  clear: () => {
    set({
      questionInputVal: "",
      answerInputVal: "",
      selectedTags: [],
      reasoningText: "",
      scoringCriteriaText: "",
      availableTags: [],
      selectedQuestion: null,
      questionList: [],
      qaReferenceList: [],
      fileList: [],
      referenceFileList: [],
      selectFileId: "",
      lastEditTime: 0,
    });
  },
}));

export const useDocumentPreviewStore = () => {
  return store(useShallow((state) => state));
};
