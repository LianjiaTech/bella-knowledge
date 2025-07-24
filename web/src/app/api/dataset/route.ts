import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const ";
import { NextRequest, NextResponse } from "next/server";

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const dataset_id = searchParams.get("dataset_id") || "";
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/get`,
    method: "POST",
    body: {
      dataset_id,
    },
  });
  return res;
}

export async function POST(req: NextRequest) {
  const data = await req.json();
  const { name, type, remark, file_id, dataset_id } = data;
  // 追加的场景
  if (file_id) {
    const formData = new FormData();
    formData.append("file_id", file_id);
    if (dataset_id) {
      formData.append("dataset_id", dataset_id);
    }
    if (name) {
      formData.append("dataset_name", name);
    }
    if (type) {
      formData.append("type", type);
    }
    if (remark) {
      formData.append("remark", remark);
    }
    const workspace = req.headers.get("X-BELLA-SPACE-CODE");
    const res = await fetch(`${FILE_API_URL}/v1/datasets/import`, {
      method: "POST",
      body: formData,
      headers: {
        "X-BELLA-CONSOLE": "true",
        "X-BELLA-SPACE-CODE": workspace || "",
        cookie: req.cookies.toString(),
      },
    });
    const resData = await res.json();
    return NextResponse.json(resData);
  } else {
    // 创建的场景
    const res = await backendRequest(req, {
      url: `${FILE_API_URL}/v1/datasets/create`,
      method: "POST",
      body: data,
    });
    return res;
  }
}

export async function DELETE(req: NextRequest) {
  const data = await req.json();
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/delete`,
    method: "POST",
    body: data,
  });
  return res;
}
export async function PUT(req: NextRequest) {
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/update`,
    method: "POST",
  });
  return res;
}
