FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json .
RUN npm install
COPY . .
ARG VITE_API_URL
ARG VITE_GOOGLE_CLIENT_ID
RUN echo "VITE_API_URL=$VITE_API_URL" > .env.production && \
    echo "VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID" >> .env.production
RUN npm run build

# Caddy serves the SPA *and* proxies /api, /auth, /ws to the backend, so this
# is the only web server in the stack — see the Caddyfile.
FROM caddy:2-alpine

COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv

EXPOSE 80 443
