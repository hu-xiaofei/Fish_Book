FROM node:24.18.0-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29.1-alpine
ARG NGINX_CONFIG=nginx.conf
COPY ${NGINX_CONFIG} /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 8080
