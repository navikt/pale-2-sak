package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.PdfModel
import no.nav.syfo.model.ValidationResult

@KtorExperimentalAPI
class PdfgenClient constructor(
    private val url: String,
    private val httpClient: HttpClient
) {
    suspend fun createPdf(payload: PdfModel): ByteArray = retry("pdfgen") {
        val httpResponse = httpClient.get<HttpStatement>(url) {
            contentType(ContentType.Application.Json)
            method = HttpMethod.Post
            body = payload
        }.execute()
        if (httpResponse.status == HttpStatusCode.OK) {
            httpResponse.call.response.receive<ByteArray>()
        } else {
            log.error("Mottok feilkode fra syfopdfgen: {}", httpResponse.status)
            throw RuntimeException("Mottok feilkode fra syfopdfgen: ${httpResponse.status}")
        }
    }
}

fun createPdfPayload(
    legeerklaring: Legeerklaering,
    validationResult: ValidationResult
): PdfModel = PdfModel(
    legeerklaering = legeerklaring,
    validationResult = validationResult
)
