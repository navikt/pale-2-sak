package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.log
import no.nav.syfo.model.AvsenderMottaker
import no.nav.syfo.model.Bruker
import no.nav.syfo.model.Dokument
import no.nav.syfo.model.Dokumentvarianter
import no.nav.syfo.model.GosysVedlegg
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.Sak
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.Vedlegg
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.imageToPDF
import no.nav.syfo.validation.validatePersonAndDNumber
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

class DokArkivClient(
    private val url: String,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String,
    private val httpClient: HttpClient
) {
    suspend fun createJournalpost(
        journalpostRequest: JournalpostRequest,
        loggingMeta: LoggingMeta
    ): JournalpostResponse {
        try {
            log.info(
                "Kall til dokarkiv Nav-Callid {}, {}", journalpostRequest.eksternReferanseId,
                fields(loggingMeta)
            )
            val httpResponse: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${accessTokenClient.getAccessToken(scope)}")
                header("Nav-Callid", journalpostRequest.eksternReferanseId)
                setBody(journalpostRequest)
                parameter("forsoekFerdigstill", true)
            }
            if (httpResponse.status == HttpStatusCode.Created || httpResponse.status == HttpStatusCode.Conflict) {
                return httpResponse.call.response.body<JournalpostResponse>()
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
    pdf: ByteArray,
    avsenderFnr: String,
    ediLoggId: String,
    signaturDato: LocalDateTime,
    validationResult: ValidationResult,
    msgId: String,
    vedlegg: List<Vedlegg>
) = JournalpostRequest(
    avsenderMottaker = when (validatePersonAndDNumber(avsenderFnr)) {
        true -> createAvsenderMottakerValidFnr(avsenderFnr, legeerklaering)
        else -> createAvsenderMottakerNotValidFnr(legeerklaering)
    },
    bruker = Bruker(
        id = legeerklaering.pasient.fnr,
        idType = "FNR"
    ),
    dokumenter = leggtilDokument(
        msgId = msgId,
        legeerklaering = legeerklaering,
        pdf = pdf,
        validationResult = validationResult,
        ediLoggId = ediLoggId,
        signaturDato = signaturDato,
        vedleggListe = vedlegg
    ),
    eksternReferanseId = ediLoggId,
    journalfoerendeEnhet = "9999",
    journalpostType = "INNGAAENDE",
    kanal = "HELSENETTET",
    sak = Sak(
        sakstype = "GENERELL_SAK"
    ),
    tema = "OPP",
    tittel = createTittleJournalpost(validationResult, signaturDato)
)

fun leggtilDokument(
    msgId: String,
    legeerklaering: Legeerklaering,
    pdf: ByteArray,
    validationResult: ValidationResult,
    ediLoggId: String,
    signaturDato: LocalDateTime,
    vedleggListe: List<Vedlegg>?
): List<Dokument> {
    val listDokument = ArrayList<Dokument>()
    listDokument.add(
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
    )
    if (!vedleggListe.isNullOrEmpty()) {
        val listVedleggDokumenter = ArrayList<Dokument>()
        vedleggListe
            .filter { vedlegg -> vedlegg.content.content.isNotEmpty() }
            .map { vedlegg -> toGosysVedlegg(vedlegg) }
            .mapNotNull { gosysVedlegg -> vedleggToPDF(gosysVedlegg) }
            .mapIndexed { index, vedlegg ->
                listVedleggDokumenter.add(
                    Dokument(
                        dokumentvarianter = listOf(
                            Dokumentvarianter(
                                filtype = findFiltype(vedlegg),
                                filnavn = "Vedlegg_nr_${index}_Legeerklaering_$msgId",
                                variantformat = "ARKIV",
                                fysiskDokument = vedlegg.content
                            )
                        ),
                        tittel = "Vedlegg til legeerklæring ${formaterDato(signaturDato)}"
                    )
                )
            }
        listVedleggDokumenter.map { vedlegg ->
            listDokument.add(vedlegg)
        }
    }
    return listDokument
}
fun toGosysVedlegg(vedlegg: Vedlegg): GosysVedlegg {
    return GosysVedlegg(
        contentType = vedlegg.type,
        content = Base64.getMimeDecoder().decode(vedlegg.content.content),
        description = vedlegg.description
    )
}

fun vedleggToPDF(vedlegg: GosysVedlegg): GosysVedlegg? {
    val filtype = findFiltype(vedlegg)
    if (filtype == "UKJENT") {
        return null
    } else if (filtype == "PDFA") {
        return vedlegg
    }
    log.info("Converting vedlegg of type ${vedlegg.contentType} to PDFA")

    val image =
        ByteArrayOutputStream().use { outputStream ->
            imageToPDF(vedlegg.content.inputStream(), outputStream)
            outputStream.toByteArray()
        }

    return GosysVedlegg(
        content = image,
        contentType = "application/pdf",
        description = vedlegg.description
    )
}

fun findFiltype(vedlegg: GosysVedlegg): String =
    when (vedlegg.contentType) {
        "application/pdf" -> "PDFA"
        "image/tiff" -> "TIFF"
        "image/png" -> "PNG"
        "image/jpeg" -> "JPEG"
        else -> "UKJENT".also { log.warn("Vedlegget er av av ukjent mimeType ${vedlegg.contentType}") }
    }

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
