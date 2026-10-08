FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
# npm ci : installation exacte du package-lock, reproductible d'un build a l'autre.
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx/snippets /etc/nginx/snippets

EXPOSE 80
