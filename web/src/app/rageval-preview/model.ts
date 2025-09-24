import { create } from "zustand";
import { useShallow } from "zustand/react/shallow";

type State = {};
type Action = {};

const store = create<State & Action>((set, get) => ({}));

export const useRagevalPreviewStore = () => {
  return store(useShallow((state) => state));
};
