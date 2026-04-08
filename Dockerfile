FROM debian:12-slim AS typst-downloader
ARG TYPST_VERSION=0.14.2
RUN apt-get update && apt-get install -y --no-install-recommends wget xz-utils ca-certificates \
    && wget -q "https://github.com/typst/typst/releases/download/v${TYPST_VERSION}/typst-x86_64-unknown-linux-musl.tar.xz" \
    && tar xf "typst-x86_64-unknown-linux-musl.tar.xz" \
    && mv "typst-x86_64-unknown-linux-musl/typst" /typst \
    && chmod +x /typst

FROM gcr.io/distroless/java21-debian12@sha256:db7c4c75e566f4e0a83efb57e65445a8ec8e2ce0564bb1667cd32ea269cac044
WORKDIR /app
COPY typst-pdf /app/typst-pdf
COPY --from=typst-downloader /typst /app/typst-pdf/typst
COPY build/install/*/lib /lib
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.syfo.ApplicationKt"]
