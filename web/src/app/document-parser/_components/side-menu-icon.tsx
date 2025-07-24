import { Block } from "@blocknote/core";
import {
  NotebookText,
  Code,
  Heading1,
  Heading2,
  Heading3,
  Heading4,
  Heading5,
  Heading6,
  Image as ImageIcon,
  List,
  Table,
  Text,
  Radical,
} from "lucide-react";

export const SideMenuIcon = ({ block }: { block: Block }) => {
  const baseProps = {
    size: 16,
    color: "#999",
    className: "mr-1",
  };
  const type = block.type;
  if (type === "heading") {
    const level = block.props.level;
    if (level === 1) {
      return <Heading1 {...baseProps} />;
    }
    if (level === 2) {
      return <Heading2 {...baseProps} />;
    }
    if (level === 3) {
      return <Heading3 {...baseProps} />;
    }
    if (level === 4) {
      return <Heading4 {...baseProps} />;
    }
    if (level === 5) {
      return <Heading5 {...baseProps} />;
    }
    if (level === 6) {
      return <Heading6 {...baseProps} />;
    }
  }
  if (type === "paragraph") {
    return <Text {...baseProps} />;
  }
  if (type === "codeBlock") {
    return <Code {...baseProps} />;
  }
  if (type.includes("ListItem")) {
    return <List {...baseProps} />;
  }
  if (type === "image") {
    return <ImageIcon {...baseProps} />;
  }
  if (type === "table") {
    return <Table {...baseProps}></Table>;
  }
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore
  if (type === "catalog") {
    return <NotebookText {...baseProps}></NotebookText>;
  }
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore
  if (type === "formula") {
    return <Radical {...baseProps} />;
  }
};
