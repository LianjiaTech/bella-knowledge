# 贡献指南

感谢您对 Bella Knowledge 项目的关注！我们欢迎所有形式的贡献，包括但不限于代码、文档、问题反馈和功能建议。

## 📋 目录

- [开始贡献](#开始贡献)
- [开发环境设置](#开发环境设置)
- [提交代码](#提交代码)
- [代码规范](#代码规范)
- [提交 Issue](#提交-issue)
- [提交 Pull Request](#提交-pull-request)
- [社区准则](#社区准则)

## 🚀 开始贡献

### 贡献方式

1. **🐛 报告 Bug** - 发现问题请提交详细的 Bug 报告
2. **💡 功能建议** - 提出新功能或改进建议
3. **📚 完善文档** - 改进项目文档和使用指南
4. **💻 代码贡献** - 修复 Bug 或实现新功能
5. **🧪 测试** - 编写或改进测试用例
6. **🌐 翻译** - 帮助翻译文档到其他语言

## 🛠️ 开发环境设置

### 系统要求

- **Java**: 8+
- **Node.js**: 20+
- **Maven**: 3.6+
- **Docker**: 20+ (可选)
- **MySQL**: 5.7+ 
- **Redis**: 6.0+

### 环境搭建

1. **克隆仓库**
   ```bash
   git clone https://github.com/your-username/bella-knowledge.git
   cd bella-knowledge
   ```

2. **后端开发环境**
   ```bash
   cd api
   # 使用阿里云镜像加速
   mvn clean compile -s settings-aliyun.xml
   # 运行测试
   mvn test
   ```

3. **前端开发环境**
   ```bash
   cd web
   npm install
   npm run dev
   ```

4. **Docker 环境（推荐）**
   ```bash
   cd docker
   docker-compose up -d
   ```

详细的开发环境配置请参考 [开发指南](./docs/development.md)。

## 📝 提交代码

### Git 工作流

我们使用标准的 GitHub 工作流：

1. **Fork 项目** 到您的 GitHub 账户
2. **创建功能分支** 从 `main` 分支创建
3. **本地开发** 并提交更改
4. **推送分支** 到您的 fork
5. **创建 Pull Request** 到主仓库

### 分支命名规范

```bash
# 功能开发
feature/add-file-upload
feature/improve-api-performance

# Bug 修复
fix/upload-timeout-issue
fix/authentication-error

# 文档更新
docs/update-api-guide
docs/add-deployment-guide

# 重构
refactor/optimize-file-service
refactor/improve-error-handling
```

### Commit 消息规范

我们使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```bash
# 格式
<type>: <description>

# 示例
feat: add file upload progress tracking
fix: resolve authentication timeout issue
docs: update API documentation
style: format code according to eslint rules
refactor: optimize database query performance
test: add unit tests for file service
chore: update dependencies
```

**Type 说明：**
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式（不影响功能）
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建工具、依赖更新等

## 🎨 代码规范

### Java 代码规范

- 使用 **4 空格缩进**
- 遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- 使用 **Lombok** 减少样板代码
- 添加适当的 **JavaDoc** 注释

```java
/**
 * 文件服务接口
 * @author Your Name
 */
@Service
public class FileService {
    
    /**
     * 上传文件
     * @param file 文件对象
     * @return 文件信息
     */
    public FileInfo uploadFile(MultipartFile file) {
        // 实现代码
    }
}
```

### TypeScript/React 代码规范

- 使用 **2 空格缩进**
- 遵循 **ESLint** 和 **Prettier** 配置
- 使用 **TypeScript** 严格模式
- 组件使用 **函数式组件** + **Hooks**

```typescript
interface FileUploadProps {
  onUpload: (file: File) => Promise<void>;
  maxSize: number;
}

export const FileUpload: React.FC<FileUploadProps> = ({ 
  onUpload, 
  maxSize 
}) => {
  // 组件实现
};
```

### 代码检查

提交前请运行：

```bash
# 后端代码检查
cd api
mvn checkstyle:check

# 前端代码检查
cd web
npm run lint
npm run type-check
```

## 🐛 提交 Issue

### Bug 报告

使用 [Bug Report 模板](../../issues/new?template=bug_report.yml) 提交，请包含：

- **清晰的标题** - 简洁描述问题
- **详细描述** - 问题的具体表现
- **复现步骤** - 如何重现该问题
- **期望行为** - 应该如何工作
- **环境信息** - 操作系统、版本等
- **错误日志** - 相关的错误信息

### 功能请求

使用 [Feature Request 模板](../../issues/new?template=feature_request.yml) 提交，请包含：

- **需求背景** - 为什么需要这个功能
- **解决方案** - 期望的实现方式
- **替代方案** - 考虑过的其他方案
- **优先级** - 功能的重要程度

## 🔄 提交 Pull Request

### PR 准备清单

在提交 PR 之前，请确保：

- [ ] 代码遵循项目规范
- [ ] 添加了必要的测试
- [ ] 所有测试通过
- [ ] 更新了相关文档
- [ ] PR 描述清晰完整

### PR 描述模板

请使用我们的 [PR 模板](../../compare) 填写详细信息：

- **变更类型** - Bug 修复、新功能等
- **影响组件** - API、Web、Docker 等
- **测试情况** - 如何测试验证
- **相关 Issue** - 关联的 Issue 编号

### 代码审查

- PR 需要至少 **1 位维护者** 的 approve
- 我们会尽快（通常 2-3 天内）进行 review
- 请及时响应 reviewer 的建议和问题
- 可能需要多轮修改才能合并

## 👥 社区准则

### 行为准则

- **尊重他人** - 友善、包容地对待每位贡献者
- **建设性沟通** - 提供有用的反馈和建议
- **专注技术** - 避免与技术无关的争论
- **耐心帮助** - 帮助新手了解项目和流程

### 沟通渠道

- **GitHub Issues** - 问题报告和功能讨论
- **GitHub Discussions** - 一般性讨论和问答
- **Pull Request** - 代码审查和技术讨论

## 🙏 致谢

感谢所有为 Bella Knowledge 项目做出贡献的开发者！您的每一个贡献都让项目变得更好。

---

## 📚 相关资源

- [部署与开发指南](./docker/README.md) - 开发环境配置、Docker部署、生产运维说明

## ❓ 需要帮助？

如果您在贡献过程中遇到任何问题，请随时：

1. 查看 [常见问题](../../wiki/FAQ)
2. 搜索 [已有 Issues](../../issues)
3. 提交新的 [Question Issue](../../issues/new)

我们会尽快为您提供帮助！🚀