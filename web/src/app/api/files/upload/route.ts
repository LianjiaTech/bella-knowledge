import { backendRequest, backendRequestFormData } from "@/lib/request/backend";
import { FILE_API_URL } from "@/lib/request/const";
import { NextRequest } from "next/server";

export async function POST(req: NextRequest) {
    const data= await req.formData();
    const file = data.get('file')
    const purpose = data.get('purpose')
    const ancestor_id = data.get('ancestor_id')
    const res = await backendRequestFormData(req, {
      url: `${FILE_API_URL}/v1/files/upload`,
      method: "POST",
      data:{
        file,
        purpose,
        ancestor_id
      }
    });
    return res;
  }
  