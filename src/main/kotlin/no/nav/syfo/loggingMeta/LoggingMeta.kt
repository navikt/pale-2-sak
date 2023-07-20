package no.nav.syfo.loggingMeta

data class LoggingMeta(
    val mottakId: String,
    val orgNr: String?,
    val msgId: String,
    val legeerklaeringId: String,
)

class TrackableException(override val cause: Throwable, val loggingMeta: LoggingMeta) :
    RuntimeException()

suspend fun <O> wrapExceptions(loggingMeta: LoggingMeta, cluster: String, block: suspend () -> O) {
    try {
        block()
    } catch (e: Exception) {
        if (cluster != "dev-gcp") {
            throw TrackableException(e, loggingMeta)
        }
    }
}
