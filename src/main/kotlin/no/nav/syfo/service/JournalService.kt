package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.Environment
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.createPdfPayload
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedLegeerklaering
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3

@KtorExperimentalAPI
class JournalService(
    private val env: Environment,
    private val sakClient: SakClient,
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient,
    private val personV3: PersonV3
) {
    suspend fun onJournalRequest(
        receivedLegeerklaering: ReceivedLegeerklaering,
        validationResult: ValidationResult,
        loggingMeta: LoggingMeta
    ) {
        wrapExceptions(loggingMeta) {
            log.info("Mottok en legeerklearing, prover aa lagre i Joark {}", StructuredArguments.fields(loggingMeta))

            val patient = fetchPerson(personV3, receivedLegeerklaering.legeerklaering.pasient.foedselsnummer, loggingMeta)

            val sak = sakClient.findOrCreateSak(receivedLegeerklaering.pasientAktoerId, receivedLegeerklaering.msgId,
                    loggingMeta)

            val pdfPayload = createPdfPayload(receivedLegeerklaering.legeerklaering, validationResult)
            val pdf = pdfgenClient.createPdf(pdfPayload)
            log.info("PDF generert {}", StructuredArguments.fields(loggingMeta))

            // val journalpostPayload = createJournalpostPayload(receivedSykmelding, sak.id.toString(), pdf, validationResult)
            // val journalpost = dokArkivClient.createJournalpost(journalpostPayload, loggingMeta)

            /*
            MELDING_LAGER_I_JOARK.inc()
            log.info("Melding lagret i Joark med journalpostId {}, {}",
                    journalpost.journalpostId,
                    StructuredArguments.fields(loggingMeta))

             */
        }
    }
}
