FROM gcr.io/distroless/java25-debian13@sha256:202ae7b10d0929fec7590927e5d14fad9b9a9b886ead6df4c555b8ae9ebeec17
WORKDIR /app
COPY typst-pdf /app/typst-pdf
COPY build/libs/app-*.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
ENV XDG_CACHE_HOME="/tmp"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]
