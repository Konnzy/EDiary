FROM node:24-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

COPY . .
ARG VITE_API_URL=http://127.0.0.1:8081/api
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

FROM nginx/nginx-unprivileged:1.31.9-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

USER 101:101
EXPOSE 8080
