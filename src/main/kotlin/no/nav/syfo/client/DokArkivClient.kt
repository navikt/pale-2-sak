package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.AvsenderMottaker
import no.nav.syfo.model.Bruker
import no.nav.syfo.model.Dokument
import no.nav.syfo.model.Dokumentvarianter
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.Sak
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.validation.validatePersonAndDNumber

@KtorExperimentalAPI
class DokArkivClient(
    private val url: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun createJournalpost(
        journalpostRequest: JournalpostRequest,
        loggingMeta: LoggingMeta
    ): JournalpostResponse = retry(callName = "dokarkiv",
        retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L)) {
        try {
            log.info("Kall til dokarkiv Nav-Callid {}, {}", journalpostRequest.eksternReferanseId,
                fields(loggingMeta))
            val httpResponse = httpClient.post<HttpStatement>(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                header("Nav-Callid", journalpostRequest.eksternReferanseId)
                body = journalpostRequest
                parameter("forsoekFerdigstill", true)
            }.execute()
            if (httpResponse.status == HttpStatusCode.Created || httpResponse.status == HttpStatusCode.Conflict) {
                httpResponse.call.response.receive<JournalpostResponse>()
            } else {
                log.error("Mottok uventet statuskode fra dokarkiv: {}, {}", httpResponse.status, fields(loggingMeta))
                throw RuntimeException("Mottok uventet statuskode fra dokarkiv: ${httpResponse.status}")
            }
        } catch (e: Exception) {
            log.warn("Oppretting av journalpost feilet: ${e.message}, {}", fields(loggingMeta))
            throw e
        }
    }
}

fun createJournalpostPayload(
    legeerklaering: Legeerklaering,
    sakId: String,
    pdf: ByteArray,
    avsenderFnr: String,
    ediLoggId: String,
    signaturDato: LocalDateTime,
    validationResult: ValidationResult
) = JournalpostRequest(
    avsenderMottaker = when (validatePersonAndDNumber(avsenderFnr)) {
        true -> createAvsenderMottakerValidFnr(avsenderFnr, legeerklaering)
        else -> createAvsenderMottakerNotValidFnr(legeerklaering)
    },
    bruker = Bruker(
        id = legeerklaering.pasient.fnr,
        idType = "FNR"
    ),
    dokumenter = listOf(
        Dokument(
        dokumentvarianter = listOf(
            Dokumentvarianter(
                filnavn = "$ediLoggId.pdf",
                filtype = "PDFA",
                variantformat = "ARKIV",
                fysiskDokument = pdf
            ),
            Dokumentvarianter(
                filnavn = "Legeerklæring Original",
                filtype = "JSON",
                variantformat = "ORIGINAL",
                fysiskDokument = objectMapper.writeValueAsBytes(legeerklaering)
            )
        ),
        tittel = createTittleJournalpost(validationResult, signaturDato),
        brevkode = "NAV 08-07.08"
    )
    ),
    eksternReferanseId = ediLoggId,
    journalfoerendeEnhet = "9999",
    journalpostType = "INNGAAENDE",
    kanal = "HELSENETTET",
    sak = Sak(
        arkivsaksnummer = sakId,
        arkivsaksystem = "GSAK"
    ),
    tema = "OPP",
    tittel = createTittleJournalpost(validationResult, signaturDato)
)

fun createAvsenderMottakerValidFnr(avsenderFnr: String, legeerklaering: Legeerklaering):
        AvsenderMottaker = AvsenderMottaker(
    id = avsenderFnr,
    idType = "FNR",
    land = "Norge",
    navn = legeerklaering.signatur.navn ?: ""
)

fun createAvsenderMottakerNotValidFnr(legeerklaering: Legeerklaering): AvsenderMottaker = AvsenderMottaker(
    land = "Norge",
    navn = legeerklaering.signatur.navn ?: ""
)

fun createTittleJournalpost(validationResult: ValidationResult, signaturDato: LocalDateTime): String {
    return if (validationResult.status == Status.INVALID) {
        "Avvist Legeerklaering ${formaterDato(signaturDato)}"
    } else {
        "Legeerklaering ${formaterDato(signaturDato)}"
    }
}
fun formaterDato(dato: LocalDateTime): String {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return dato.format(formatter)
}
