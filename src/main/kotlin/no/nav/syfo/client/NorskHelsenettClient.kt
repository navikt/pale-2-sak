package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import java.io.IOException

class NorskHelsenettClient(
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClient,
    private val resourceId: String,
    private val httpClient: HttpClient,
) {

    suspend fun getByFnr(fnr: String, loggingMeta: LoggingMeta): Behandler? {
        val accessToken = accessTokenClient.getAccessToken(resourceId)

        val httpResponse = httpClient.get("$endpointUrl/api/v2/behandler") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $accessToken")
                append("Nav-CallId", loggingMeta.msgId)
                append("behandlerFnr", fnr)
            }
        }
        when (httpResponse.status) {
            InternalServerError -> {
                log.error(
                    "Syfohelsenettproxy kastet feilmelding for loggingMeta {} ved henting av behandler for fnr",
                    fields(loggingMeta),
                )
                throw IOException("Syfohelsenettproxy kastet feilmelding og svarte status ${httpResponse.status} ved søk på fnr")
            }

            NotFound -> {
                log.warn("Fant ikke behandler for fnr {}", fields(loggingMeta))
                return null
            }

            Unauthorized -> {
                log.error("Norsk helsenett returnerte Unauthorized for henting av behandler")
                throw RuntimeException("Norsk helsenett returnerte Unauthorized ved henting av behandler")
            }

            OK -> {
                log.info("Hentet behandler for fnr {}", fields(loggingMeta))
                return httpResponse.body<Behandler>()
            }

            else -> {
                log.error("Feil ved henting av behandler. Statuskode: ${httpResponse.status}")
                throw RuntimeException("En ukjent feil oppsto ved ved henting av behandler. Statuskode: ${httpResponse.status}")
            }
        }
    }
}

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val hprNummer: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
)

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null,
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?,
)
