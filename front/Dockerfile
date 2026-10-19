# --- Build ---
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Type-check and lint already run in CI before the image is built.
RUN npm run build-only

# --- Runtime ---
FROM nginx:1.29-alpine

# Rendered by the nginx entrypoint (envsubst) into /etc/nginx/conf.d/default.conf.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

# Address of the back container, as seen from this container.
ENV BACKEND_URL=http://back:8080
# Request body limit of /api, above the back STORY_PDF_MAX_SIZE (20MB by default).
ENV MAX_UPLOAD_SIZE=25m

EXPOSE 80
