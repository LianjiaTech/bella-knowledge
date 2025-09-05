<div align="center">

# bella-knowledge

<h3>Bella知识管理中心</h3>

[![License](https://img.shields.io/badge/License-MIT-blue?style=flat)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-1.8+-orange?style=flat)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.12-brightgreen?style=flat)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19.0-blue?style=flat)](https://reactjs.org/)
[![GitHub stars](https://img.shields.io/github/stars/LianjiaTech/bella-knowledge?style=flat)](https://github.com/LianjiaTech/bella-knowledge/stargazers)

</div>

<p align="center">
  <b>中文</b> | 
  <a href="./README_EN.md">English</a>
</p>

## 🔥 项目简介

**bella-knowledge** 是 **Bella 体系内的知识管理中心**，提供文件、数据集在内多类数据源的统一存储、管理能力。

## 🚀 项目亮点

**🎯 OpenAI File API 兼容**
- 系统完全**对标 OpenAI File API**，在标准化接口的基础上，扩展了企业级的数据管理和知识处理功能

**📁 统一数据管理**
- 文件、数据集、知识库的集中式存储和管理
- 跨系统的数据源统一接入和标准化处理
- 多格式数据的智能解析和结构化处理

**🔗 生态系统集成**
- 为 Bella-Workflow 提供可靠的数据源支撑
- 为 Bella-RAG 系统提供高质量的知识库构建能力
- 支持整个 Bella 体系内的数据流转和共享

**📊 智能数据处理**
- 快速制作训练数据集和评测数据集
- 支持构建标准化的 QA 问答对和评测基准
- 提供数据质量评估和优化建议

## 💪 核心优势

- **🏛️ 企业级可靠性** - 在大规模生产环境验证，支持高并发、高可用的企业级应用场景
- **🔌 标准化兼容** - 完全兼容 OpenAI File API，无缝集成现有 AI 应用生态
- **🌐 生态协同** - 与 Bella-Workflow、Bella-RAG 等深度集成，实现数据无缝流转
- **⚡ 智能处理** - 内置多种文档解析引擎，支持 PDF、Office、图片等多格式智能处理
- **🔐 安全可控** - 支持私有化部署，企业数据完全自主可控
- **📈 弹性扩展** - 微服务架构设计，支持水平扩展和灵活配置

系统采用现代化的微服务架构，为整个 Bella 生态提供稳定、高效的知识管理基础设施。

## 🏗️ 系统架构

### 技术栈

[![Java](https://img.shields.io/badge/Java-8+-orange?style=flat&logo=java)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3+-brightgreen?style=flat&logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19+-blue?style=flat&logo=react)](https://reactjs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-5.7+-blue?style=flat&logo=mysql)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-6.0+-red?style=flat&logo=redis)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=flat&logo=docker)](https://www.docker.com/)

### 项目结构

```
bella-knowledge/
├── api/                    # 后端服务
│   ├── src/               # 源代码
│   ├── sql/               # 数据库脚本
│   └── pom.xml           # Maven配置
├── web/                   # 前端应用
│   ├── src/              # React源代码
│   └── package.json      # 依赖配置
├── docker/               # Docker部署配置
├── mysql/                # MySQL数据目录
├── nginx/                # Nginx配置
└── redis/                # Redis数据目录
```

## 📍 快速开始

### Docker 部署（推荐）

```bash
# 克隆项目
git clone https://github.com/LianjiaTech/bella-knowledge.git
cd bella-knowledge

# 使用Docker Compose启动
cd docker
docker-compose up -d
```

### 访问地址

- 前端应用: http://localhost:3000
- 后端API: http://localhost:8080

## 📖 文档

### 📚 快速导航

- **[🚀 部署与开发指南](./docker/README.md)** - Docker部署、开发环境、生产运维完整指南
- **[🤝 贡献指南](./CONTRIBUTING.md)** - 代码规范、提交流程、如何参与项目贡献

### 🐛 问题反馈

遇到问题？请查看：

1. **[常见问题](../../wiki/FAQ)** - 查看常见问题解答
2. **[提交 Bug](../../issues/new?template=bug_report.yml)** - 报告 Bug 或问题
3. **[功能建议](../../issues/new?template=feature_request.yml)** - 提出新功能建议
4. **[GitHub Issues](../../issues)** - 查看所有问题和讨论

### 🛠️ 开发指南

详细的开发环境搭建、配置说明和部署指南，请参考：
- **[部署与开发指南](./docker/README.md)** - 包含开发环境配置、Docker部署、生产运维等完整指南

## 📄 许可协议

本项目采用 MIT 许可协议，详细条款请参阅 [LICENSE](./LICENSE) 文件。

---

<div align="center">
  <p>© 2025 Bella Knowledge. 保留所有权利。</p>
</div>