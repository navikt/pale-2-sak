package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.storage.Storage
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.model.VedleggMessage
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta

class BucketService(
    private val vedleggBucketName: String,
    private val legeerklaeringBucketName: String,
    private val storage: Storage,
) {
    fun getVedleggFromBucket(key: String, loggingMeta: LoggingMeta): Vedlegg {
        val vedleggBlob = storage.get(vedleggBucketName, key)

        if (vedleggBlob == null) {
            log.error("Fant ikke vedlegg med key $key {}", StructuredArguments.fields(loggingMeta))
            throw RuntimeException("Fant ikke vedlegg med key $key")
        } else {
            log.info("Fant vedlegg med key $key", StructuredArguments.fields(loggingMeta))
            return objectMapper.readValue<VedleggMessage>(vedleggBlob.getContent()).vedlegg
        }
    }

    fun getLegeerklaeringFromBucket(key: String): ReceivedLegeerklaering {
        val legeerklaeringBlob = storage.get(legeerklaeringBucketName, key)

        if (legeerklaeringBlob == null) {
            log.error("Fant ikke legeerklæring med key $key {}")
            throw RuntimeException("Fant ikke legeerklæring med key $key")
        } else {
            log.info("Fant legeerklæring med key $key")
            return objectMapper.readValue<ReceivedLegeerklaering>(legeerklaeringBlob.getContent())
        }
    }
}
