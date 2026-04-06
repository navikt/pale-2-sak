package no.nav.syfo.journalpost.createJournalPost

import com.google.cloud.storage.Storage
import io.opentelemetry.instrumentation.annotations.WithSpan
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.bucket.getVedlegg.getLegeerklaeringVedlegg
import no.nav.syfo.client.dokArkivClient.DokArkivClient
import no.nav.syfo.client.dokArkivClient.createJournalpostPayload
import no.nav.syfo.client.norskHelsenettClient.NorskHelsenettClient
import no.nav.syfo.client.pdfgenrs.PdfgenrsClient
import no.nav.syfo.client.pdfgenrs.createPdfrsPayload
import no.nav.syfo.logger
import no.nav.syfo.loggingMeta.LoggingMeta
import no.nav.syfo.loggingMeta.wrapExceptions
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.secureLogger
import no.nav.syfo.objectMapper

@WithSpan
suspend fun onJournalRequest(
    dokArkivClient: DokArkivClient,
    pdfgenrsClient: PdfgenrsClient,
    legeerklaeringVedleggBucketName: String,
    storage: Storage,
    norskHelsenettClient: NorskHelsenettClient,
    receivedLegeerklaering: ReceivedLegeerklaering,
    validationResult: ValidationResult,
    vedlegg: List<String>?,
    loggingMeta: LoggingMeta,
    cluster: String,
) {
    wrapExceptions(loggingMeta, cluster) {
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

        
            val pdfrsPayload =
                createPdfrsPayload(
                    receivedLegeerklaering.legeerklaering,
                    validationResult,
                    receivedLegeerklaering.mottattDato,
                )
            val pdfrs = pdfgenrsClient.creatersPdf(pdfrsPayload)
            logger.info("PDFRS generert {}", StructuredArguments.fields(loggingMeta))
            secureLogger.info("receivedLegeerklaering.legeerklaering: {}", objectMapper.writeValueAsString(receivedLegeerklaering.legeerklaering))


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
                pdfrs,
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
