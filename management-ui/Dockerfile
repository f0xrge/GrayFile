FROM node:22-alpine AS build
WORKDIR /app

COPY management-ui/package*.json ./
RUN npm install

COPY management-ui/ ./
RUN npm run build

FROM nginx:1.29-alpine
COPY management-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/grayfile-management-ui/browser /usr/share/nginx/html
