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

type State = {
  questionInputVal: string;
  answerInputVal: string;
  selectedQuestion: Question | null;
  questionList: QuestionList;
  qaReferenceList: QaReferenceList;
  fileList: KnowledgeFile[];
  referenceFileList: KnowledgeFile[];
  uploadProgress: number;
  selectFileId: string;
  lastEditTime: number;
  initLoading: boolean;
};
type Action = {
  onChangeQuestionInputVal: (val: string) => void;
  onChangeAnswerInputVal: (val: string) => void;
  onChangeSelectedQuestion: (question: Question) => void;
  addQuestion: (body: {
    dataset_id: string;
    question: string;
    answer: string;
  }) => void;
  deleteQuestion: (question: Question) => void;
  updateQuestion: (question: Question) => void;
  getQuestionList: (dataset_id: string) => void;
  addQuestionReference: (params: {
    dataset_id: string;
    item_id: string;
    file_id: string;
    path: number[];
  }) => Promise<void>;
  deleteQuestionReference: (body: {
    dataset_id: string;
    reference_id: number;
  }) => void;
  getReferenceList: (dataset_id: string, item_id: string) => void;
  getFileList: () => void;
  getReferenceFileList: (dataset_id: string) => Promise<void>;
  addReferenceFile: (file: KnowledgeFile) => void;
  uploadFile: (file: File) => Promise<string | null>;
  getUploadProgress: (fileId: string) => Promise<number | null>;
  initPage: (dataset_id: string) => Promise<void>;
  initReferenceFileList: (dataset_id: string) => Promise<void>;
  setSelectFileId: (fileId: string) => void;
  clear: () => void;
};

const store = create<State & Action>((set, get) => ({
  questionInputVal: "",
  answerInputVal: "",
  questionList: [],
  qaReferenceList: [],
  selectedQuestion: null,
  fileList: [],
  referenceFileList: [],
  uploadProgress: 0,
  selectFileId: "",
  lastEditTime: 0,
  initLoading: false,
  getQuestionList: async (dataset_id: string) => {
    const res = await webRequest<Question[]>({
      path: "/api/qa/list",
      method: "GET",
      query: {
        dataset_id: dataset_id,
      },
    });
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
        });
      }
      set({
        questionList: questionList.filter(
          (question) => question.item_id !== item_id
        ),
        lastEditTime: Date.now(),
      });
      toast.success("删除成功");
    }
  },
  updateQuestion: async ({ dataset_id, item_id, question, answer }) => {
    const { questionList } = get();
    const currentQuestion = questionList.find(
      (question) => question.item_id === item_id
    );
    if (
      currentQuestion?.question === question &&
      currentQuestion?.answer === answer
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
      },
    });
    if (res.code === 200) {
      set({
        questionList: questionList.map((question) =>
          question.item_id === item_id ? res.data : question
        ),
        selectedQuestion: res.data,
        lastEditTime: Date.now(),
      });
      toast.success("更新成功");
    }
  },
  addQuestionReference: async (body) => {
    const { qaReferenceList } = get();
    const qaReference = qaReferenceList.find(
      (qaReference) => qaReference.item_id === body.item_id
    );
    if (
      qaReference?.references.find(
        (r) => r.file_id === body.file_id && r.path.join() === body.path.join()
      )
    ) {
      toast.error("当前文件的该节点已存在标注");
      return;
    }
    const res = await webRequest<{ reference_id: number }>({
      path: "/api/qa-reference",
      method: "POST",
      body,
    });

    if (res.code === 200) {
      const newReference = {
        file_id: body.file_id,
        path: body.path,
        reference_id: res.data.reference_id,
      };

      if (qaReference) {
        set({
          qaReferenceList: qaReferenceList.map((qaReference) =>
            qaReference.item_id === body.item_id
              ? {
                  ...qaReference,
                  references: [...qaReference.references, newReference],
                }
              : qaReference
          ),
          lastEditTime: Date.now(),
        });
      } else {
        set({
          qaReferenceList: [
            ...qaReferenceList,
            {
              item_id: body.item_id,
              references: [newReference],
            },
          ],
          lastEditTime: Date.now(),
        });
      }
    } else {
      toast.error(res.message);
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
            (r) => r.reference_id !== body.reference_id
          ),
        })),
        lastEditTime: Date.now(),
      });
      toast.success("删除成功");
    }
  },
  getReferenceList: async (dataset_id: string, item_id: string) => {
    const { qaReferenceList } = get();
    const qaReference = qaReferenceList.find(
      (qaReference) => qaReference.item_id === item_id
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
      });
      const { getReferenceList } = get();
      getReferenceList(question.dataset_id, question.item_id.toString());
    }
  },
  getFileList: async () => {
    const res = await webRequest<{ data: KnowledgeFile[] }>({
      path: "/api/files",
      method: "GET",
    });
    if (res.code === 200) {
      set({
        fileList: res.data.data,
      });
    }
  },
  uploadFile: async (file) => {
    const formData = new FormData();
    formData.append("file", file);
    const currentWorkspace = JSON.parse(
      localStorage.getItem("current_workspace") || "{}"
    );

    const response = await fetch("/api/files", {
      method: "POST",
      body: formData,
      headers: {
        "X-USER-ID": localStorage.getItem("user_id") || "",
        "X-BELLA-SPACE-CODE": currentWorkspace.spaceCode || "",
      },
    });
    const data = await response.json();
    if (data.code === 200) {
      set({
        fileList: [data.data, ...get().fileList],
      });
      return data.data.id;
    }
    return null;
  },
  getUploadProgress: async (fileId) => {
    return new Promise((resolve) => {
      const poll = async () => {
        const res = await webRequest<{
          percent: number;
          code: number;
          status: "document_parse_finish" | "document_parse_failed";
        }>({
          path: "/api/dom-tree/progress",
          method: "GET",
          query: {
            fileId: fileId,
          },
        });
        if (res.data.code === 404) {
          set({
            uploadProgress: 0,
          });
        } else {
          set({
            uploadProgress: res.data.percent,
          });
        }
        if (res.data.status === "document_parse_finish") {
          resolve(100);
        } else {
          setTimeout(poll, 1000);
        }
      };
      poll();
    });
  },
  addReferenceFile: async (file: KnowledgeFile) => {
    const { referenceFileList } = get();
    set({
      referenceFileList: [...referenceFileList, file],
    });
  },

  getReferenceFileList: async (dataset_id: string) => {
    const { questionList, fileList } = get();
    const res = await webRequest<string[]>({
      path: "/api/reference-files",
      method: "POST",
      body: {
        dataset_id,
        item_ids: questionList.map((item) => item.item_id),
      },
    });

    if (res.code === 200) {
      set({
        referenceFileList: fileList.filter((file) =>
          res.data.includes(file.id)
        ),
      });
    }
  },
  initPage: async (dataset_id: string) => {
    set({ initLoading: true });
    await Promise.all([get().getQuestionList(dataset_id), get().getFileList()]);
    set({ initLoading: false });
  },
  initReferenceFileList: async (dataset_id: string) => {
    const { questionList } = get();
    console.log(questionList);
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
        limit: number
      ) => {
        const results: string[][] = [];

        for (let i = 0; i < batches.length; i += limit) {
          const currentBatch = batches.slice(i, i + limit);
          const promises = currentBatch.map((batch) =>
            singleReq(batch.map((item: Question) => item.item_id.toString()))
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
  setSelectFileId: (fileId: string) => {
    set({ selectFileId: fileId });
  },
  clear: () => {
    set({
      questionInputVal: "",
      answerInputVal: "",
      selectedQuestion: null,
      questionList: [],
      qaReferenceList: [],
      fileList: [],
      referenceFileList: [],
      uploadProgress: 0,
      selectFileId: "",
      lastEditTime: 0,
    });
  },
}));

export const useDocumentPreviewStore = () => {
  return store(useShallow((state) => state));
};
