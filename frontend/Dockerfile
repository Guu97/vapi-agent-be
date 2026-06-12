FROM nginx:1.27-alpine

WORKDIR /usr/share/nginx/html

RUN rm -rf ./*

COPY index.html ./
COPY app.js ./
COPY runtime-config.js ./

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]