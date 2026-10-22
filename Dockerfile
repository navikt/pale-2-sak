FROM gcr.io/distroless/java25-debian13@sha256:00cb938f4bae711dee23dab0b2fdb1ed36a5a242283193e49ec05d271ad8263c
WORKDIR /app
COPY typst-pdf /app/typst-pdf
COPY build/libs/pale-2-sak-*-all.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
ENV XDG_CACHE_HOME="/tmp"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]
