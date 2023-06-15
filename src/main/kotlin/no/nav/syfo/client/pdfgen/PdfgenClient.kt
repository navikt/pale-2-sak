package no.nav.syfo.client.pdfgen

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.time.LocalDateTime
import no.nav.syfo.logger
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper

class PdfgenClient
constructor(
    private val url: String,
    private val httpClient: HttpClient,
) {
    suspend fun createPdf(payload: PdfModel): ByteArray {
        val httpResponse: HttpResponse =
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        if (httpResponse.status == HttpStatusCode.OK) {
            return httpResponse.call.response.body<ByteArray>()
        } else {
            logger.error("Mottok feilkode fra pale-2-pdfgen: {}", httpResponse.status)
            throw RuntimeException("Mottok feilkode fra pale-2-pdfgen: ${httpResponse.status}")
        }
    }
}

fun createPdfPayload(
    legeerklaring: Legeerklaering,
    validationResult: ValidationResult,
    mottattDato: LocalDateTime,
): PdfModel =
    PdfModel(
        legeerklaering = mapToLegeerklaringWithoutIllegalCharacters(legeerklaring),
        validationResult = validationResult,
        mottattDato = mottattDato,
    )

fun mapToLegeerklaringWithoutIllegalCharacters(legeerklaring: Legeerklaering): Legeerklaering {
    val legeerklaringAsString = objectMapper.writeValueAsString(legeerklaring)
    val legeerklaringAsStringWithoutIllegalCharacters =
        legeerklaringAsString.replace(regex = Regex("\\p{C}"), "")
    return objectMapper.readValue(
        legeerklaringAsStringWithoutIllegalCharacters,
        Legeerklaering::class.java
    )
}
