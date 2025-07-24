import { redirect } from "next/navigation";

const NotFound = () => {
  redirect("/admin");
};

export default NotFound;
