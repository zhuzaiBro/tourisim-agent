# ============================================
# 前端多阶段构建
# ============================================

# 构建阶段
FROM node:20-slim AS builder
WORKDIR /app

# 接受构建时环境变量（Vite 仅打包 VITE_ 前缀变量）
ARG VITE_API_BASE_URL=https://tourisim-api.zood.work
ARG VITE_AMAP_KEY=
ARG VITE_AMAP_SECURITY_CODE=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_AMAP_KEY=$VITE_AMAP_KEY
ENV VITE_AMAP_SECURITY_CODE=$VITE_AMAP_SECURITY_CODE

COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps

COPY . .
RUN node node_modules/vite/bin/vite.js build

# ============================================
# 运行阶段：Nginx 静态文件服务
# ============================================
FROM nginx:alpine AS runtime

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# Nginx 配置：SPA 路由支持 + API 代理
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
