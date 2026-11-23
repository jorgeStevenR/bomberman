# API de resultados (Node.js). Imagen pequeña (alpine) y sin usuario root.
FROM node:22-alpine

WORKDIR /app
ENV NODE_ENV=production

# Primero solo package*.json: si el código cambia pero las dependencias no,
# Docker reutiliza la capa de "npm install" (builds mucho más rápidos).
COPY package*.json ./
RUN npm install --omit=dev --no-audit --no-fund && npm cache clean --force

COPY src ./src

# El usuario "node" ya existe en la imagen oficial: nunca ejecutamos como root.
USER node
EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost:3000/api/health || exit 1

CMD ["node", "src/server.js"]
