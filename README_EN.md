<div align="center">

# bella-knowledge

<h3>Bella Knowledge Management Center</h3>

[![License](https://img.shields.io/badge/License-MIT-blue?style=flat)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-1.8+-orange?style=flat)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.12-brightgreen?style=flat)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19.0-blue?style=flat)](https://reactjs.org/)
[![GitHub stars](https://img.shields.io/badge/GitHub-stars-yellow?style=flat&logo=github)](https://github.com/LianjiaTech/bella-knowledge/stargazers)

</div>

<p align="center">
  <a href="./README.md">中文</a> | 
  <b>English</b>
</p>

## 🔥 Overview

**bella-knowledge** is the **knowledge management center within the Bella ecosystem**, providing unified storage and management capabilities for multiple data sources including files and datasets.

## 🚀 Key Features

**🎯 OpenAI File API Compatible**
- Fully **compatible with OpenAI File API**, extending enterprise-grade data management and knowledge processing capabilities on top of standardized interfaces

**📁 Unified Data Management**
- Centralized storage and management of files, datasets, and knowledge bases
- Unified access and standardized processing of cross-system data sources
- Intelligent parsing and structured processing of multi-format data

**🔗 Ecosystem Integration**
- Provides reliable data source support for Bella-Workflow
- Offers high-quality knowledge base construction capabilities for Bella-RAG systems
- Supports data flow and sharing throughout the entire Bella ecosystem

**📊 Intelligent Data Processing**
- Rapid creation of training datasets and evaluation datasets
- Support for building standardized QA pairs and evaluation benchmarks
- Data quality assessment and optimization recommendations

## 💪 Core Advantages

- **🏛️ Enterprise-grade Reliability** - Validated in large-scale production environments, supporting high-concurrency, high-availability enterprise scenarios
- **🔌 Standard Compatibility** - Fully compatible with OpenAI File API, seamlessly integrating existing AI application ecosystems
- **🌐 Ecosystem Synergy** - Deep integration with Bella-Workflow, Bella-RAG, etc., enabling seamless data flow
- **⚡ Intelligent Processing** - Built-in multiple document parsing engines, supporting intelligent processing of PDF, Office, images, and other formats
- **🔐 Security & Control** - Supports private deployment, complete enterprise data autonomy and control
- **📈 Elastic Scaling** - Microservice architecture design, supporting horizontal scaling and flexible configuration

The system adopts modern microservice architecture, providing stable and efficient knowledge management infrastructure for the entire Bella ecosystem.

## ✨ Feature Matrix

<table>
  <tr>
    <th width="200">Feature Module</th>
    <th>Description</th>
  </tr>
  <tr>
    <td><b>🏛️ Knowledge Management Center</b></td>
    <td>Serves as the unified knowledge management center for the Bella ecosystem, centrally managing multiple data sources including files and datasets</td>
  </tr>
  <tr>
    <td><b>🎯 OpenAI API Compatible</b></td>
    <td>Fully compatible with OpenAI File API, providing standardized file management interfaces for seamless integration with existing AI applications</td>
  </tr>
  <tr>
    <td><b>📊 Smart Dataset Creation</b></td>
    <td>Rapid creation of training and evaluation datasets, supporting intelligent processing of both structured and unstructured data</td>
  </tr>
  <tr>
    <td><b>🔍 Intelligent Document Parsing</b></td>
    <td>Built-in parsing engines for PDF, Excel, CSV, and other document formats, automatically extracting content and metadata</td>
  </tr>
  <tr>
    <td><b>🤖 Bella-RAG Integration</b></td>
    <td>Deep integration with Bella-RAG systems, providing high-quality knowledge base support for retrieval-augmented generation</td>
  </tr>
  <tr>
    <td><b>📋 Evaluation Dataset Construction</b></td>
    <td>Support for building standardized QA pairs and evaluation datasets for AI model performance assessment</td>
  </tr>
  <tr>
    <td><b>🔄 Bella Ecosystem Flow</b></td>
    <td>Seamless file and data flow within the Bella ecosystem, supporting workflow automation</td>
  </tr>
  <tr>
    <td><b>🏷️ Smart Tagging System</b></td>
    <td>AI-driven tag management system supporting intelligent classification and retrieval of files and datasets</td>
  </tr>
  <tr>
    <td><b>☁️ Multi-Cloud Storage</b></td>
    <td>Support for AWS S3, Alibaba Cloud OSS, and other cloud storage services with flexible storage strategy configuration</td>
  </tr>
  <tr>
    <td><b>🔐 Enterprise-grade Permissions</b></td>
    <td>Fine-grained permission management based on organizations and users, supporting multi-tenant architecture and data isolation</td>
  </tr>
</table>

## 🏗️ System Architecture

### Technology Stack

[![Java](https://img.shields.io/badge/Java-8+-orange?style=flat&logo=java)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3+-brightgreen?style=flat&logo=spring)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19+-blue?style=flat&logo=react)](https://reactjs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-5.7+-blue?style=flat&logo=mysql)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-6.0+-red?style=flat&logo=redis)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=flat&logo=docker)](https://www.docker.com/)

### Project Structure

```
bella-knowledge/
├── api/                    # Backend service
│   ├── src/               # Source code
│   ├── sql/               # Database scripts
│   └── pom.xml           # Maven configuration
├── web/                   # Frontend application
│   ├── src/              # React source code
│   └── package.json      # Dependencies configuration
├── docker/               # Docker deployment configuration
├── mysql/                # MySQL data directory
├── nginx/                # Nginx configuration
└── redis/                # Redis data directory
```

## 📍 Quick Start

### Docker Deployment (Recommended)

```bash
# Clone the project
git clone https://github.com/LianjiaTech/bella-knowledge.git
cd bella-knowledge

# Start with Docker Compose
cd docker
docker-compose up -d
```

### Access URLs

- Frontend App: http://localhost:3000
- Backend API: http://localhost:8080
- API Documentation: http://localhost:8080/swagger-ui.html

## 🛠️ Development Guide

For detailed development environment setup, configuration instructions, and deployment guide, please refer to the [Docker Deployment Documentation](./docker/README.md).

## 📖 API Documentation

The system provides complete REST APIs that are fully compatible with OpenAI File API standards, while extending dataset and tag management functionality.

For detailed API documentation, visit the Swagger UI interface: `http://localhost:8080/swagger-ui.html`

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](./LICENSE) file for details.

---

<div align="center">
  <p>© 2025 bella-knowledge. All rights reserved.</p>
</div>