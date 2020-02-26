package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
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
            log.info("Kall til dokakriv Nav-Callid {}, {}", journalpostRequest.eksternReferanseId,
                fields(loggingMeta))
            httpClient.post<JournalpostResponse>(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${stsClient.oidcToken().access_token}")
                header("Nav-Callid", journalpostRequest.eksternReferanseId)
                body = journalpostRequest
                parameter("forsoekFerdigstill", true)
            }
        } catch (e: Exception) {
            log.warn("Oppretting av journalpost feilet: ${e.message}, {}", fields(loggingMeta))
            throw e
        }
    }
}

fun createJournalpostPayload(
    legeerklaering: Legeerklaering,
    pasientfnr: String,
    caseId: String,
    pdf: ByteArray,
    msgHead: XMLMsgHead,
    receiverBlock: XMLMottakenhetBlokk,
    validationResult: ValidationResult
) = JournalpostRequest(
    avsenderMottaker = when (validatePersonAndDNumber(receiverBlock.avsenderFnrFraDigSignatur)) {
        true -> createAvsenderMottakerValidFnr(receiverBlock)
        else -> createAvsenderMottakerNotValidFnr()
    },
    bruker = Bruker(
        id = pasientfnr,
        idType = "FNR"
    ),
    dokumenter = listOf(
        Dokument(
        dokumentvarianter = listOf(
            Dokumentvarianter(
                filnavn = "Legeerklæring",
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
        tittel = createTittleJournalpost(validationResult, msgHead),
        brevkode = "NAV 08-07.08"
    )
    ),
    eksternReferanseId = receiverBlock.ediLoggId,
    journalfoerendeEnhet = "9999",
    journalpostType = "INNGAAENDE",
    kanal = "HELSENETTET",
    sak = Sak(
        arkivsaksnummer = caseId,
        arkivsaksystem = "GSAK"
    ),
    tema = "OPP",
    tittel = createTittleJournalpost(validationResult, msgHead)
)

fun createAvsenderMottakerValidFnr(receiverBlock: XMLMottakenhetBlokk): AvsenderMottaker = AvsenderMottaker(
    id = receiverBlock.avsenderFnrFraDigSignatur,
    idType = "FNR",
    land = "Norge",
    navn = ""
)

fun createAvsenderMottakerNotValidFnr(): AvsenderMottaker = AvsenderMottaker(
    land = "Norge",
    navn = ""
)

fun createTittleJournalpost(validationResult: ValidationResult, msgHead: XMLMsgHead): String {
    return if (validationResult.status == Status.INVALID) {
        "Avvist Legeerklaering ${formaterDato(msgHead.msgInfo.genDate)}"
    } else {
        "Legeerklaering ${formaterDato(msgHead.msgInfo.genDate)}"
    }
}
fun formaterDato(dato: LocalDateTime): String {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return dato.format(formatter)
}
