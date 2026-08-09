import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  allowedDevOrigins: process.env.NEXT_PUBLIC_ALLOWED_DEV_ORIGINS
    ? process.env.NEXT_PUBLIC_ALLOWED_DEV_ORIGINS.split(",")
    : [],
  images: {
    remotePatterns: [new URL("https://img.ljcdn.com/*")],
  },
  turbopack: {
    root: path.resolve(__dirname),
  },
  // 使用 standalone 输出模式
  output: "standalone",
  typescript: {
    ignoreBuildErrors: true,
  },
};

export default nextConfig;
