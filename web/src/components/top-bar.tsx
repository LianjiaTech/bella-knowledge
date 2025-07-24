"use client";
import React, { useEffect } from "react";

import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useUserStore } from "@/store/user";
import { Workspace } from "@/lib/types/user";
import { Button } from "./ui/button";
import { FileText, User } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import Image from "next/image";
import logo from "@/assets/logo-site.png";

const SpaceSelect = ({
  spaceList,
  onChange,
  currentWorkspace,
}: {
  spaceList: Workspace[];
  onChange: (spaceCode: string) => void;
  currentWorkspace: Workspace | null;
}) => {
  return (
    <Select
      onValueChange={(value) => {
        onChange(value);
      }}
      value={currentWorkspace?.spaceCode}
    >
      <SelectTrigger className="w-[180px]">
        <SelectValue placeholder="选择空间" />
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          <SelectLabel>空间</SelectLabel>
          {spaceList.map((space) => (
            <SelectItem key={space.spaceCode} value={space.spaceCode}>
              {space.spaceName}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  );
};
const LINK = [
  {
    link: "/admin",
    text: "数据集",
    icon: <FileText className="size-4" />,
  },
];
const TopBarTabs = () => {
  const pathname = usePathname();
  const isActive = (link: string) => {
    return pathname.startsWith(link);
  };
  return (
    <div className="flex h-5 items-center  space-x-4 ">
      {LINK.map((link, index) => (
        <div key={index}>
          <Link href={link.link}>
            <Button
              variant="link"
              className={cn(
                "text-base hover:bg-gray-100",
                isActive(link.link) && "text-blue-500 font-bold bg-gray-100",
              )}
            >
              {link.icon}
              {link.text}
            </Button>
          </Link>
        </div>
      ))}
    </div>
  );
};

const TopBar = () => {
  const {
    userInfo,
    currentWorkspace,
    getWorkspaceList,
    changeCurrentWorkspace,
    getUserInfo,
  } = useUserStore();
  const { workspaceList } = useUserStore();
  useEffect(() => {
    getUserInfo();
  }, [getUserInfo]);
  useEffect(() => {
    if (!userInfo) {
      return;
    }
    getWorkspaceList();
  }, [userInfo, getWorkspaceList]);
  return (
    <div className="flex justify-between  items-center p-6 h-16 shadow-md relative">
      <div className="flex items-center gap-4">
        <Image src={logo} alt="logo" width={100} height={100} />
        <SpaceSelect
          spaceList={workspaceList}
          onChange={changeCurrentWorkspace}
          currentWorkspace={currentWorkspace}
        />
      </div>
      <TopBarTabs />
      <div className="flex items-center gap-2">
        <User className="box-content size-6 rounded-full text-gray-500" />
        <div className="text-base font-medium">{userInfo?.userName}</div>
      </div>
    </div>
  );
};

export default TopBar;
