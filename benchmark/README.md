# Local performance benchmark

这套 benchmark 在隔离的 Docker 环境中启动 MySQL、Redis、MinIO 和当前源码构建出的 API，随后通过容器化 k6 执行负载。它不会使用开发环境的数据卷，也不要求本机预装 k6。

## 一键运行

仓库根目录执行：

```bash
./benchmark/bench
```

默认行为：

1. 删除上一次 benchmark 的容器和数据卷。
2. 构建并启动隔离环境。
3. 生成 1 MiB 上传样本。
4. 依次运行 `smoke`、`files-read`、`files-upload`、`files-move`、`files-cross-shard-move`、`datasets`、`mixed`。
5. 将完整 k6 JSON 和汇总报告写入 `benchmark/results/`。

环境会在执行后保持运行，方便检查指标和日志。完成后执行：

```bash
./benchmark/bench clean
```

## 常用命令

```bash
# 只跑一个场景；缺少环境时会自动启动
./benchmark/bench run files-read

# 启停环境
./benchmark/bench up
./benchmark/bench down

# 停止环境并删除 benchmark 数据
./benchmark/bench clean

# 根据已有 JSON 重新生成报告
./benchmark/bench report
```

支持的场景：

| 场景 | 覆盖内容 |
|---|---|
| `smoke` | 建目录、上传、查询、分页、移动、祖先查询、数据集和 QA 完整链路 |
| `files-read` | 单文件查询、文件列表、首页/深页分页、批量祖先查询 |
| `files-upload` | 不同文件名的并发流式上传 |
| `files-move` | 不同目标深度、指定子树规模下的并发目录移动 |
| `files-cross-shard-move` | 将大目录树从源空间迁移到不同数据库分片的目标空间，并单独统计迁移耗时 |
| `datasets` | dataset 分页、QA 查询/分页以及并发写入 |
| `mixed` | 文件读、分页、上传、建目录和移动的混合负载 |

## 调整负载

```bash
# 8 个 VU 持续 60 秒
BENCH_VUS=8 BENCH_DURATION=60s ./benchmark/bench run mixed

# 上传 16 MiB 文件
BENCH_UPLOAD_BYTES=16777216 BENCH_VUS=4 ./benchmark/bench run files-upload

# 增大读场景数据集
BENCH_SEED_FILES=1000 ./benchmark/bench run files-read

# 移动 100 个节点的子树，目标目录深度为 10
BENCH_MOVE_SUBTREE_SIZE=100 BENCH_MOVE_TARGET_DEPTH=10 ./benchmark/bench run files-move

# 将 5000 节点、分支因子 20 的目录树跨空间跨分片移动一次
BENCH_CROSS_SHARD_SUBTREE_SIZE=5000 \
BENCH_CROSS_SHARD_BRANCHING=20 \
./benchmark/bench run files-cross-shard-move

# 复用已经构建好的 API 镜像
BENCH_BUILD=0 ./benchmark/bench run smoke
```

主要环境变量：

| 变量 | 默认值 | 说明 |
|---|---:|---|
| `BENCH_DURATION` | `30s` | 非 smoke 场景持续时间 |
| `BENCH_VUS` | `4` | 并发虚拟用户数 |
| `BENCH_P95_MS` | `1000` | 通用 p95 门禁，单位毫秒 |
| `BENCH_UPLOAD_BYTES` | `1048576` | 上传样本大小 |
| `BENCH_SEED_FILES` | `40` | files-read 预置文件条数 |
| `BENCH_DATASET_SEED_QAS` | `30` | datasets 预置 QA 条数 |
| `BENCH_MOVE_SUBTREE_SIZE` | `10` | files-move 子树节点总数 |
| `BENCH_MOVE_TARGET_DEPTH` | `3` | files-move 目标目录深度 |
| `BENCH_CROSS_SHARD_TARGET_SPACE` | `cross-shard-benchmark` | 跨分片移动的目标空间；必须与 `BENCH_SPACE_CODE` 落在不同分片 |
| `BENCH_CROSS_SHARD_SUBTREE_SIZE` | `1000` | 跨分片移动的目录树节点总数 |
| `BENCH_CROSS_SHARD_BRANCHING` | `10` | 大子树每个目录最多创建的直接子目录数 |
| `BENCH_CROSS_SHARD_MOVE_WORKERS` | `1` | 独立大子树迁移样本数 |
| `BENCH_CROSS_SHARD_P95_MS` | `10000` | 跨分片迁移独立 p95 门禁，单位毫秒 |
| `BENCH_TASK_THREADS` | `16` | API 后台任务工作线程数 |
| `BENCH_TASK_QUEUE_CAPACITY` | `1000` | API 后台任务队列容量 |
| `BENCH_API_HEAP` | `1024m` | API 的固定 Xms/Xmx |
| `BENCH_BUILD` | `1` | 是否重新构建 API 镜像 |
| `BENCH_FRESH` | `1` | `all` 执行前是否删除旧数据卷 |

## 结果说明

`benchmark/results/report.md` 汇总每个场景的：

- requests/s
- p50、p95、p99
- HTTP 失败率
- k6 check 成功率

每次运行还会保留完整的 k6 JSON，文件名包含场景和时间。默认门禁为：

- check 成功率大于 99%
- HTTP 失败率小于 1%
- 应用级错误率小于 1%
- p95 小于 `BENCH_P95_MS`

首次运行主要用于建立本机基线。长期回归时，应在相同机器、相同 Docker 资源和相同数据规模下比较结果。

## 诊断

```bash
# API 日志
docker compose -f benchmark/docker-compose.yml logs -f api

# Prometheus 指标
curl -H 'Authorization: Bearer local-dev' \
  http://127.0.0.1:18082/actuator/prometheus

# MySQL
docker compose -f benchmark/docker-compose.yml exec mysql \
  mysql -ubella_user -p123456 bella_file_api
```
