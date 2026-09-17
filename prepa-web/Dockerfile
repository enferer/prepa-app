FROM node:22-alpine AS build
WORKDIR /build

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Caddy sert les fichiers, relaie /api vers l'API et termine le TLS. Un seul conteneur
# en facade plutot qu'un serveur de fichiers derriere un terminateur TLS : sur une petite
# machine, chaque processus compte.
FROM caddy:2-alpine
COPY --from=build /build/dist /srv
COPY Caddyfile /etc/caddy/Caddyfile
EXPOSE 443
