package no.nav.syfo.client.dokArkivClient

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
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.accessToken.AccessTokenClient
import no.nav.syfo.journalpost.createJournalPost.AvsenderMottaker
import no.nav.syfo.journalpost.createJournalPost.Bruker
import no.nav.syfo.journalpost.createJournalPost.Dokument
import no.nav.syfo.journalpost.createJournalPost.Dokumentvarianter
import no.nav.syfo.journalpost.createJournalPost.GosysVedlegg
import no.nav.syfo.journalpost.createJournalPost.JournalpostRequest
import no.nav.syfo.journalpost.createJournalPost.JournalpostResponse
import no.nav.syfo.journalpost.createJournalPost.Vedlegg
import no.nav.syfo.logger
import no.nav.syfo.loggingMeta.LoggingMeta
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper

class DokArkivClient(
    private val url: String,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String,
    private val httpClient: HttpClient,
) {
    suspend fun createJournalpost(
        journalpostRequest: JournalpostRequest,
        loggingMeta: LoggingMeta,
    ): JournalpostResponse {
        try {
            logger.info(
                "Kall til dokarkiv Nav-Callid {}, {}",
                journalpostRequest.eksternReferanseId,
                fields(loggingMeta),
            )
            val httpResponse: HttpResponse =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header(
                        "Authorization",
                        "Bearer ${accessTokenClient.getAccessToken(scope, loggingMeta)}"
                    )
                    header("Nav-Callid", journalpostRequest.eksternReferanseId)
                    setBody(journalpostRequest)
                    parameter("forsoekFerdigstill", false)
                }
            if (
                httpResponse.status == HttpStatusCode.Created ||
                    httpResponse.status == HttpStatusCode.Conflict
            ) {
                return httpResponse.call.response.body<JournalpostResponse>()
            } else {
                logger.error(
                    "Mottok uventet statuskode fra dokarkiv: {}, {}",
                    httpResponse.status,
                    fields(loggingMeta)
                )
                throw RuntimeException(
                    "Mottok uventet statuskode fra dokarkiv: ${httpResponse.status}"
                )
            }
        } catch (e: Exception) {
            logger.error("Oppretting av journalpost feilet: ${e.message}, {}", fields(loggingMeta))
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
    vedlegg: List<Vedlegg>,
    hprNr: String?,
) =
    JournalpostRequest(
        avsenderMottaker =
            if (hprNr != null) {
                createAvsenderMottakerValidHpr(hprNr.trim(), legeerklaering)
            } else {
                when (validatePersonAndDNumber(avsenderFnr)) {
                    true -> createAvsenderMottakerValidFnr(avsenderFnr, legeerklaering)
                    else -> createAvsenderMottakerNotValidFnr(legeerklaering)
                }
            },
        bruker =
            Bruker(
                id = legeerklaering.pasient.fnr,
                idType = "FNR",
            ),
        dokumenter =
            leggtilDokument(
                msgId = msgId,
                legeerklaering = legeerklaering,
                pdf = pdf,
                validationResult = validationResult,
                ediLoggId = ediLoggId,
                signaturDato = signaturDato,
                vedleggListe = vedlegg,
            ),
        eksternReferanseId = ediLoggId,
        journalfoerendeEnhet = "9999",
        journalpostType = "INNGAAENDE",
        kanal = "HELSENETTET",
        tema = "AAP",
        tittel = createTittleJournalpost(validationResult, signaturDato),
    )

fun leggtilDokument(
    msgId: String,
    legeerklaering: Legeerklaering,
    pdf: ByteArray,
    validationResult: ValidationResult,
    ediLoggId: String,
    signaturDato: LocalDateTime,
    vedleggListe: List<Vedlegg>?,
): List<Dokument> {
    val listDokument = ArrayList<Dokument>()
    listDokument.add(
        Dokument(
            dokumentvarianter =
                listOf(
                    Dokumentvarianter(
                        filnavn = "$ediLoggId.pdf",
                        filtype = "PDFA",
                        variantformat = "ARKIV",
                        fysiskDokument = pdf,
                    ),
                    Dokumentvarianter(
                        filnavn = "Legeerklæring Original",
                        filtype = "JSON",
                        variantformat = "ORIGINAL",
                        fysiskDokument = objectMapper.writeValueAsBytes(legeerklaering),
                    ),
                ),
            tittel = createTittleJournalpost(validationResult, signaturDato),
            brevkode = "NAV 08-07.08",
        ),
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
                        dokumentvarianter =
                            listOf(
                                Dokumentvarianter(
                                    filtype = findFiltype(vedlegg),
                                    filnavn = "Vedlegg_nr_${index}_Legeerklaering_$msgId",
                                    variantformat = "ARKIV",
                                    fysiskDokument = vedlegg.content,
                                ),
                            ),
                        tittel = "Vedlegg til legeerklæring ${formaterDato(signaturDato)}",
                    ),
                )
            }
        listVedleggDokumenter.map { vedlegg -> listDokument.add(vedlegg) }
    }
    return listDokument
}

fun toGosysVedlegg(vedlegg: Vedlegg): GosysVedlegg {
    return GosysVedlegg(
        contentType = vedlegg.type,
        content = Base64.getMimeDecoder().decode(vedlegg.content.content),
        description = vedlegg.description,
    )
}

fun vedleggToPDF(vedlegg: GosysVedlegg): GosysVedlegg? {
    val filtype = findFiltype(vedlegg)
    if (filtype == "UKJENT") {
        return null
    } else if (filtype == "PDFA") {
        return vedlegg
    }
    logger.info("Converting vedlegg of type ${vedlegg.contentType} to PDFA")

    val image =
        ByteArrayOutputStream().use { outputStream ->
            imageToPDF(vedlegg.content.inputStream(), outputStream)
            outputStream.toByteArray()
        }

    return GosysVedlegg(
        content = image,
        contentType = "application/pdf",
        description = vedlegg.description,
    )
}

fun findFiltype(vedlegg: GosysVedlegg): String =
    when (vedlegg.contentType) {
        "application/pdf" -> "PDFA"
        "image/tiff" -> "TIFF"
        "image/png" -> "PNG"
        "image/jpeg" -> "JPEG"
        else ->
            "UKJENT"
                .also { logger.warn("Vedlegget er av av ukjent mimeType ${vedlegg.contentType}") }
    }

fun createAvsenderMottakerValidFnr(
    avsenderFnr: String,
    legeerklaering: Legeerklaering
): AvsenderMottaker =
    AvsenderMottaker(
        id = avsenderFnr,
        idType = "FNR",
        land = "Norge",
        navn = legeerklaering.signatur.navn ?: "",
    )

fun createAvsenderMottakerNotValidFnr(legeerklaering: Legeerklaering): AvsenderMottaker =
    AvsenderMottaker(
        land = "Norge",
        navn = legeerklaering.signatur.navn ?: "",
    )

fun createAvsenderMottakerValidHpr(
    hprNr: String,
    legeerklaering: Legeerklaering
): AvsenderMottaker =
    AvsenderMottaker(
        id = hprnummerMedRiktigLengde(hprNr),
        idType = "HPRNR",
        land = "Norge",
        navn = legeerklaering.signatur.navn ?: "",
    )

fun createTittleJournalpost(
    validationResult: ValidationResult,
    signaturDato: LocalDateTime
): String {
    return if (validationResult.status == Status.INVALID) {
        "Avvist Legeerklæring ${formaterDato(signaturDato)}"
    } else {
        "Legeerklæring ${formaterDato(signaturDato)}"
    }
}

fun formaterDato(dato: LocalDateTime): String {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return dato.format(formatter)
}

private fun hprnummerMedRiktigLengde(hprnummer: String): String {
    if (hprnummer.length < 9) {
        return hprnummer.padStart(9, '0')
    }
    return hprnummer
}
