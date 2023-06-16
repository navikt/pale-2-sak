package no.nav.syfo.journalpost.createJournalPost

import com.google.cloud.storage.Storage
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.bucket.getVedlegg.getLegeerklaeringVedlegg
import no.nav.syfo.client.dokArkivClient.DokArkivClient
import no.nav.syfo.client.dokArkivClient.createJournalpostPayload
import no.nav.syfo.client.norskHelsenettClient.NorskHelsenettClient
import no.nav.syfo.client.pdfgen.PdfgenClient
import no.nav.syfo.client.pdfgen.createPdfPayload
import no.nav.syfo.logger
import no.nav.syfo.loggingMeta.LoggingMeta
import no.nav.syfo.loggingMeta.wrapExceptions
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.secureLogger

suspend fun onJournalRequest(
    dokArkivClient: DokArkivClient,
    pdfgenClient: PdfgenClient,
    legeerklaeringVedleggBucketName: String,
    storage: Storage,
    norskHelsenettClient: NorskHelsenettClient,
    receivedLegeerklaering: ReceivedLegeerklaering,
    validationResult: ValidationResult,
    vedlegg: List<String>?,
    loggingMeta: LoggingMeta,
) {
    wrapExceptions(loggingMeta) {
        logger.info(
            "Mottok en legeerklearing, prover aa lagre i Joark {}",
            StructuredArguments.fields(loggingMeta),
        )
        secureLogger.info(
            "Mottok en legeerklearing for fnr {}, prover aa lagre i Joark {}",
            receivedLegeerklaering.personNrPasient,
            StructuredArguments.fields(loggingMeta),
        )

        val vedleggListe: List<Vedlegg> =
            if (vedlegg.isNullOrEmpty()) {
                emptyList()
            } else {
                logger.info(
                    "Legeerklæringen har ${vedlegg.size} vedlegg {}",
                    StructuredArguments.fields(loggingMeta),
                )
                vedlegg.map {
                    getLegeerklaeringVedlegg(
                        legeerklaeringVedleggBucketName,
                        storage,
                        it,
                        loggingMeta,
                    )
                }
            }

        val pdfPayload =
            createPdfPayload(
                receivedLegeerklaering.legeerklaering,
                validationResult,
                receivedLegeerklaering.mottattDato,
            )
        val pdf = pdfgenClient.createPdf(pdfPayload)
        logger.info("PDF generert {}", StructuredArguments.fields(loggingMeta))

        val behandler =
            try {
                norskHelsenettClient.getByFnr(
                    fnr = receivedLegeerklaering.personNrLege,
                    loggingMeta = loggingMeta,
                )
            } catch (exception: Exception) {
                logger.warn("Feilet å hente behandler med fnr: ", exception)
                null
            }

        val journalpostPayload =
            createJournalpostPayload(
                receivedLegeerklaering.legeerklaering,
                pdf,
                receivedLegeerklaering.personNrLege,
                receivedLegeerklaering.navLogId,
                receivedLegeerklaering.legeerklaering.signaturDato,
                validationResult,
                receivedLegeerklaering.msgId,
                vedleggListe,
                behandler?.hprNummer,
            )
        val journalpost = dokArkivClient.createJournalpost(journalpostPayload, loggingMeta)

        MELDING_LAGER_I_JOARK.inc()
        logger.info(
            "Melding lagret i Joark med journalpostId {}, {}",
            journalpost.journalpostId,
            StructuredArguments.fields(loggingMeta),
        )
    }
}
