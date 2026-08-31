package no.nav.syfo.bucket.getLegeerklaering

import com.google.cloud.storage.Storage
import no.nav.syfo.jsonMapper
import no.nav.syfo.logger
import no.nav.syfo.model.ReceivedLegeerklaering
import tools.jackson.module.kotlin.readValue

fun getLegeerklaering(bucketName: String, storage: Storage, key: String): ReceivedLegeerklaering {

    val legeerklaeringBlob = storage.get(bucketName, key)

    if (legeerklaeringBlob == null) {
        logger.error("Fant ikke legeerklæring med key $key {}")
        throw RuntimeException("Fant ikke legeerklæring med key $key")
    } else {
        logger.info("Fant legeerklæring med key $key")
        return jsonMapper.readValue<ReceivedLegeerklaering>(legeerklaeringBlob.getContent())
    }
}
