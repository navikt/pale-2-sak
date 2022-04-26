package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.syfo.log
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.PdfModel
import no.nav.syfo.model.ValidationResult

class PdfgenClient constructor(
    private val url: String,
    private val httpClient: HttpClient
) {
    suspend fun createPdf(payload: PdfModel): ByteArray {
        val httpResponse: HttpResponse = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (httpResponse.status == HttpStatusCode.OK) {
            return httpResponse.call.response.body<ByteArray>()
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
