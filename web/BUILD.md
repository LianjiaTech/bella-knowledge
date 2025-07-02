# 构建说明

## 构建配置

此 Next.js 项目已配置为将静态资源和服务端代码分离到不同的目录：

- **静态资源**: `../dist/client/` - 包含 CSS、JS、图片等静态文件
- **服务端代码**: `../dist/server/` - 包含服务器运行所需的文件

## 构建命令

```bash
npm run build
```

这个命令会：

1. 执行 `next build` 构建项目
2. 自动运行 `scripts/organize-build.js` 脚本组织文件

## 目录结构

构建完成后的目录结构（从项目根目录查看）：

```
example-file-api/
├── web/                 # Next.js项目源码
└── dist/                # 构建输出目录
    ├── client/
    │   └── static/      # 静态资源（CSS, JS, 图片等）
    │       ├── css/
    │       ├── chunks/
    │       └── media/
    └── server/
        ├── server.js    # 服务器入口文件
        ├── package.json # 依赖信息
        └── dist/        # Next.js构建文件
            ├── server/
            └── ...
```

## 部署

- **静态资源**: 将项目根目录的 `dist/client/` 目录部署到 CDN 或静态文件服务器
- **服务端应用**: 将项目根目录的 `dist/server/` 目录部署到 Node.js 服务器

## 启动服务端

在服务器上，进入项目根目录的 `dist/server/` 目录：

```bash
cd dist/server
node server.js
```

## 自定义配置

如需修改构建行为，可以编辑：

- `web/next.config.ts` - Next.js 配置
- `web/scripts/organize-build.js` - 文件组织脚本
