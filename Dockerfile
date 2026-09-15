FROM gcr.io/distroless/java25-debian13@sha256:1d7a0cea4653f62be34a5b9b1da82a4dd097ae8935d1d3f4ab84146e0396fd2b
WORKDIR /app
COPY typst-pdf /app/typst-pdf
COPY build/libs/pale-2-sak-*-all.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
ENV XDG_CACHE_HOME="/tmp"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]
