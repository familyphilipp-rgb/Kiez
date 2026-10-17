FROM node:22-alpine
RUN apk add --no-cache ffmpeg
WORKDIR /app
COPY package.json server.js media.js ./
COPY public ./public
RUN mkdir -p /data/uploads && chown -R node:node /data
ENV NODE_ENV=production \
    PORT=3000 \
    DB_FILE=/data/kiez.db \
    UPLOAD_DIR=/data/uploads
VOLUME /data
EXPOSE 3000
USER node
CMD ["node", "--disable-warning=ExperimentalWarning", "server.js"]
