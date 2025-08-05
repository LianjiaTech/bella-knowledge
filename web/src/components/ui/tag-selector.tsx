"use client";

import * as React from "react";
import { Check, Search, Tag as TagIcon } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tag, requestCreateTag } from "@/request/tags";

interface TagSelectorProps {
  selectedTags: string[];
  availableTags: Tag[];
  onTagsChange: (tags: string[]) => void;
  onRefreshTags: () => void;
  onBlur?: (tags?: string[]) => void;
  placeholder?: string;
  className?: string;
}

export function TagSelector({
  selectedTags,
  availableTags,
  onTagsChange,
  onRefreshTags,
  onBlur,
  placeholder = "添加标签",
  className,
}: TagSelectorProps) {
  const [open, setOpen] = React.useState(false);
  const [searchValue, setSearchValue] = React.useState("");
  const [tempSelectedTags, setTempSelectedTags] = React.useState<string[]>(selectedTags);
  const [isOverflowing, setIsOverflowing] = React.useState(false);
  const [isCreatingTag, setIsCreatingTag] = React.useState(false);
  const containerRef = React.useRef<HTMLDivElement>(null);

  // 同步外部selectedTags的变化，但只在下拉菜单关闭时才同步
  React.useEffect(() => {
    if (!open) {
      setTempSelectedTags(selectedTags);
    }
  }, [selectedTags, open]);

  // 检测内容是否溢出
  React.useEffect(() => {
    const checkOverflow = () => {
      if (containerRef.current && selectedTags.length > 0) {
        const container = containerRef.current;
        setIsOverflowing(container.scrollWidth > container.clientWidth);
      } else {
        setIsOverflowing(false);
      }
    };

    checkOverflow();

    // 监听窗口大小变化
    const resizeObserver = new ResizeObserver(checkOverflow);
    if (containerRef.current) {
      resizeObserver.observe(containerRef.current);
    }

    return () => {
      resizeObserver.disconnect();
    };
  }, [selectedTags]);

  // 处理下拉菜单关闭时更新状态并触发blur
  const handleOpenChange = (newOpen: boolean) => {
    if (!newOpen && open) {
      onTagsChange(tempSelectedTags);
      onBlur?.(tempSelectedTags);
    }
    setOpen(newOpen);
  };

  const filteredTags = React.useMemo(() => {
    let tags = availableTags;
    if (searchValue) {
      tags = tags.filter((tag) =>
        tag.name.toLowerCase().includes(searchValue.toLowerCase())
      );
    }
    
    // 对标签进行排序：已选中的在前面，未选中的在后面
    return tags.sort((a, b) => {
      const aSelected = tempSelectedTags.includes(a.name);
      const bSelected = tempSelectedTags.includes(b.name);
      
      if (aSelected && !bSelected) return -1;
      if (!aSelected && bSelected) return 1;
      
      // 如果都选中或都未选中，保持原顺序
      return 0;
    });
  }, [availableTags, searchValue, tempSelectedTags]);

  const handleTagToggle = (tagName: string) => {
    const newTags = tempSelectedTags.includes(tagName)
      ? tempSelectedTags.filter((tag) => tag !== tagName)
      : [...tempSelectedTags, tagName];
    setTempSelectedTags(newTags);
  };

  const handleCreateTag = async () => {
    if (searchValue.trim() && !availableTags.some(tag => tag.name === searchValue.trim())) {
      const tagName = searchValue.trim();
      setIsCreatingTag(true);
      
      try {
        // 调用后端接口创建标签
        await requestCreateTag(tagName);
        
        // 创建成功后，将标签添加到已选中列表
        const newTags = [...tempSelectedTags, tagName];
        setTempSelectedTags(newTags);
        
        // 清空搜索值
        setSearchValue("");
        
        // 重新拉取标签列表以确保新标签在列表中
        onRefreshTags();
      } catch (error) {
        console.error("Failed to create tag:", error);
        // 可以在这里添加错误提示
      } finally {
        setIsCreatingTag(false);
      }
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && searchValue.trim() && !isCreatingTag) {
      handleCreateTag();
    }
  };

  return (
    <DropdownMenu open={open} onOpenChange={handleOpenChange}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn(
            "w-full justify-start text-left font-normal",
            selectedTags.length === 0 && "text-muted-foreground",
            className
          )}
        >
          <TagIcon className="mr-2 h-4 w-4 shrink-0" />
          <div className="flex-1 min-w-0 overflow-hidden">
            {selectedTags.length > 0 ? (
              <div 
                ref={containerRef}
                className="inline-flex items-center gap-1 overflow-hidden"
                style={isOverflowing ? {
                  maskImage: 'linear-gradient(to right, black calc(100% - 20px), transparent 100%)',
                  WebkitMaskImage: 'linear-gradient(to right, black calc(100% - 20px), transparent 100%)'
                } : undefined}
              >
                {selectedTags.map((tag) => (
                  <span
                    key={tag}
                    className="inline-flex items-center rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-800 whitespace-nowrap shrink-0"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            ) : (
              <span className="truncate">{placeholder}</span>
            )}
          </div>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-80 p-0" align="start">
        <div className="p-3 border-b">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="搜索或者创建"
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              onKeyDown={handleKeyDown}
              className="pl-10"
            />
          </div>
        </div>
        
        <div className="max-h-60 overflow-auto">
          {filteredTags.map((tag) => (
            <div
              key={tag.id}
              className={cn(
                "flex items-center justify-between px-3 py-2 cursor-pointer hover:bg-muted/50",
                tempSelectedTags.includes(tag.name) && "bg-blue-50"
              )}
              onClick={() => handleTagToggle(tag.name)}
            >
              <div className="flex items-center space-x-2">
                <div className={cn(
                  "h-4 w-4 border rounded flex items-center justify-center",
                  tempSelectedTags.includes(tag.name)
                    ? "bg-blue-600 border-blue-600"
                    : "border-gray-300"
                )}>
                  {tempSelectedTags.includes(tag.name) && (
                    <Check className="h-3 w-3 text-white" />
                  )}
                </div>
                <span className="text-sm">{tag.name}</span>
              </div>
            </div>
          ))}
          
          {searchValue.trim() && !filteredTags.some(tag => tag.name === searchValue.trim()) && (
            <div
              className={cn(
                "flex items-center px-3 py-2 text-blue-600",
                isCreatingTag ? "cursor-not-allowed opacity-50" : "cursor-pointer hover:bg-muted/50"
              )}
              onClick={!isCreatingTag ? handleCreateTag : undefined}
            >
              <span className="text-sm">
                {isCreatingTag ? `正在创建 "${searchValue.trim()}"...` : `创建 "${searchValue.trim()}"`}
              </span>
            </div>
          )}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}