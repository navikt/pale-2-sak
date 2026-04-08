FROM debian:12-slim AS typst-downloader
ARG TYPST_VERSION=0.14.2
ARG TYPST_SHA256=a6044cbad2a954deb921167e257e120ac0a16b20339ec01121194ff9d394996d
RUN apt-get update && apt-get install -y --no-install-recommends wget xz-utils ca-certificates \
    && wget -q "https://github.com/typst/typst/releases/download/v${TYPST_VERSION}/typst-x86_64-unknown-linux-musl.tar.xz" \
    && echo "${TYPST_SHA256}  typst-x86_64-unknown-linux-musl.tar.xz" | sha256sum -c - \
    && tar xf "typst-x86_64-unknown-linux-musl.tar.xz" \
    && mv "typst-x86_64-unknown-linux-musl/typst" /typst \
    && chmod +x /typst \
    && rm -rf typst-x86_64-unknown-linux-musl.tar.xz typst-x86_64-unknown-linux-musl

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
