import { backendRequest } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const dataset_id = searchParams.get("dataset_id") || "";
  const item_id = searchParams.get("item_id") || "";
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/get`,
    method: "POST",
    body: { dataset_id, item_id },
  });
  return res;
}

export async function POST(req: NextRequest) {
  const body = await req.json();
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/create`,
    method: "POST",
    body,
  });
  return res;
}

export async function PUT(req: NextRequest) {
  const body = await req.json();
  const { dataset_id, item_id, question, answer, reasoning, scoring_criteria, tags } = body;
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/update`,
    method: "POST",
    body: {
      dataset_id,
      item_id: item_id,
      question,
      answer,
      reasoning,
      scoring_criteria: scoring_criteria,
      tags,
    },
  });
  return res;
}

export async function DELETE(req: NextRequest) {
  const body = await req.json();
  const { dataset_id, item_id } = body;
  const res = await backendRequest(req, {
    url: `${FILE_API_URL}/v1/datasets/qa/delete`,
    method: "POST",
    body: { dataset_id, item_id },
  });
  return res;
}
