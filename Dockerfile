FROM debian:bookworm-slim AS typst-setup
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl xz-utils && rm -rf /var/lib/apt/lists/*
RUN curl -fSL "https://github.com/typst/typst/releases/download/v0.14.2/typst-x86_64-unknown-linux-musl.tar.xz" \
         -o /tmp/typst.tar.xz && \
    tar -xJf /tmp/typst.tar.xz -C /tmp && \
    chmod +x /tmp/typst-x86_64-unknown-linux-musl/typst
RUN mkdir -p /typst-pdf/fonts && \
    BASE="https://raw.githubusercontent.com/navikt/pale-2-pdfgenrs/main/fonts" && \
    for font in SourceSansPro-Regular SourceSansPro-Bold SourceSansPro-Italic SourceSansPro-BoldItalic \
                SourceSansPro-Light SourceSansPro-LightItalic SourceSansPro-SemiBold SourceSansPro-SemiBoldItalic \
                SourceSansPro-ExtraLight SourceSansPro-ExtraLightItalic SourceSansPro-Black SourceSansPro-BlackItalic; do \
        curl -fSL "$BASE/${font}.ttf" -o "/typst-pdf/fonts/${font}.ttf"; \
    done
RUN mkdir -p /typst-pdf/resources && \
    BASE="https://raw.githubusercontent.com/navikt/pale-2-pdfgenrs/main/resources" && \
    curl -fSL "$BASE/NAVLogoRed.png" -o /typst-pdf/resources/NAVLogoRed.png && \
    curl -fSL "$BASE/NAVLogoRedSantaHat.png" -o /typst-pdf/resources/NAVLogoRedSantaHat.png

FROM gcr.io/distroless/java21-debian12@sha256:db7c4c75e566f4e0a83efb57e65445a8ec8e2ce0564bb1667cd32ea269cac044
WORKDIR /app
COPY build/install/*/lib /lib
COPY --from=typst-setup /tmp/typst-x86_64-unknown-linux-musl/typst /app/typst-pdf/typst
COPY --from=typst-setup /typst-pdf/fonts /app/typst-pdf/fonts
COPY --from=typst-setup /typst-pdf/resources /app/typst-pdf/resources
COPY typst-pdf/ /app/typst-pdf/
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-cp", "/lib/*", "no.nav.syfo.ApplicationKt"]
