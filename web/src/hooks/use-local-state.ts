import { useEffect, useState } from "react";

export function useLocalState<T>(
  key: string,
  defaultValue: T,
): [T, (value: T) => void] {
  const [state, setState] = useState<T>(defaultValue);

  useEffect(() => {
    const value = localStorage.getItem(key);
    if (value) {
      setState(JSON.parse(value));
    }
  }, [key]);

  const setLocalState = (value: T) => {
    localStorage.setItem(key, JSON.stringify(value));
    setState(value);
  };
  return [state, setLocalState];
}
