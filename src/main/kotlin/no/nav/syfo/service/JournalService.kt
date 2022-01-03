package no.nav.syfo.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.createJournalpostPayload
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.log
import no.nav.syfo.metrics.MELDING_LAGER_I_JOARK
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions

class JournalService(
    private val sakClient: SakClient,
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient
) {
    suspend fun onJournalRequest(
        receivedLegeerklaering: ReceivedLegeerklaering,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta
    ) {
        wrapExceptions(loggingMeta) {
            log.info("Mottok en legeerklearing, prover aa lagre i Joark {}", StructuredArguments.fields(loggingMeta))

            val sak = sakClient.findOrCreateSak(
                receivedLegeerklaering.pasientAktoerId, receivedLegeerklaering.msgId,
                loggingMeta
            )

            val pdfPayload = createPdfPayload(receivedLegeerklaering.legeerklaering, validationResult)
            val pdf = pdfgenClient.createPdf(pdfPayload)
            log.info("PDF generert {}", StructuredArguments.fields(loggingMeta))

            val journalpostPayload = createJournalpostPayload(
                receivedLegeerklaering.legeerklaering,
                sak.id.toString(),
                pdf,
                receivedLegeerklaering.personNrLege,
                receivedLegeerklaering.navLogId,
                receivedLegeerklaering.legeerklaering.signaturDato,
                validationResult
            )
            val journalpost = dokArkivClient.createJournalpost(journalpostPayload, loggingMeta)

            MELDING_LAGER_I_JOARK.inc()
            log.info(
                "Melding lagret i Joark med journalpostId {}, {}",
                journalpost.journalpostId,
                StructuredArguments.fields(loggingMeta)
            )
        }
    }
}
