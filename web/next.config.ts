import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  allowedDevOrigins: ["example.com"],
  images: {
    remotePatterns: [new URL("https://img.ljcdn.com/*")],
  },

  // 使用standalone输出模式
  output: "standalone",

  eslint: {
    dirs: [
      "src",
      "components",
      "hooks",
      "lib",
      "pages",
      "styles",
      "types",
      "utils",
      "app",
    ],
  },
};

export default nextConfig;
