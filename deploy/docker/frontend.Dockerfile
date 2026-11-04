# Frontend image: build the Vite SPA, then serve it with Nginx (also proxies /api).
# Build context must be the repository root:
#   docker build -f deploy/docker/frontend.Dockerfile -t icp-frontend .
FROM node:22-alpine AS build
WORKDIR /src
COPY frontend/ frontend/
RUN cd frontend && (npm ci --no-audit --no-fund || npm install --no-audit --no-fund) \
    && npm run build

FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/frontend/dist /usr/share/nginx/html
EXPOSE 80
