# base image
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/node:20.16.0-alpine3.20 AS base
LABEL maintainer="takatost@gmail.com"

# 安装 pnpm
RUN npm install -g pnpm

RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories
RUN apk add --no-cache tzdata


# install packages
FROM base AS packages

WORKDIR /app/web

COPY package.json pnpm-lock.yaml* ./

# if you located in China, you can use taobao registry to speed up
RUN pnpm install --frozen-lockfile --registry https://registry.npmmirror.com/

# build resources
FROM base AS builder
WORKDIR /app/web
COPY --from=packages /app/web/ .
COPY . .

RUN pnpm build

# production stage
FROM base AS production

ENV NODE_ENV=production
ENV EDITION=SELF_HOSTED
ENV DEPLOY_ENV=PRODUCTION
ENV PORT=3000

# set timezone
ENV TZ=UTC
RUN ln -s /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo ${TZ} > /etc/timezone

# global runtime packages
RUN npm install -g pm2


WORKDIR /app/web
COPY --from=builder /app/web/public ./public
COPY --from=builder /app/web/.next/standalone ./
COPY --from=builder /app/web/.next/static ./.next/static


COPY docker/pm2.json ./pm2.json
COPY docker/entrypoint.sh ./entrypoint.sh

ARG COMMIT_SHA
ENV COMMIT_SHA=${COMMIT_SHA}

EXPOSE 3000
ENTRYPOINT ["/bin/sh", "./entrypoint.sh"]
