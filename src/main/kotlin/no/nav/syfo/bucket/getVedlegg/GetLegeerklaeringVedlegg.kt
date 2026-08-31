package no.nav.syfo.bucket.getVedlegg

import com.google.cloud.storage.Storage
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.journalpost.createJournalPost.Vedlegg
import no.nav.syfo.journalpost.createJournalPost.VedleggMessage
import no.nav.syfo.jsonMapper
import no.nav.syfo.logger
import no.nav.syfo.loggingMeta.LoggingMeta
import tools.jackson.module.kotlin.readValue

fun getLegeerklaeringVedlegg(
    bucketName: String,
    storage: Storage,
    key: String,
    loggingMeta: LoggingMeta,
): Vedlegg {

    val vedleggBlob = storage.get(bucketName, key)

    if (vedleggBlob == null) {
        logger.error("Fant ikke vedlegg med key $key {}", StructuredArguments.fields(loggingMeta))
        throw RuntimeException("Fant ikke vedlegg med key $key")
    } else {
        logger.info("Fant vedlegg med key $key", StructuredArguments.fields(loggingMeta))
        return jsonMapper.readValue<VedleggMessage>(vedleggBlob.getContent()).vedlegg
    }
}
