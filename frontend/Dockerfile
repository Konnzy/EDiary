FROM node:24-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

COPY . .
ARG VITE_API_URL=/api
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

FROM nginx/nginx-unprivileged:1.31.9-alpine

COPY nginx.conf /opt/ediary/nginx.conf.template
COPY start-nginx.sh /opt/ediary/start-nginx.sh
COPY --from=build /app/dist /usr/share/nginx/html

USER 101:101
EXPOSE 8080
CMD ["/bin/sh", "/opt/ediary/start-nginx.sh"]
