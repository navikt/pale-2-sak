package no.nav.syfo.legeerklaring

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.storage.Storage
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.ApplicationState
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.bucket.getLegeerklaering.getLegeerklaering
import no.nav.syfo.client.dokArkivClient.DokArkivClient
import no.nav.syfo.client.norskHelsenettClient.NorskHelsenettClient
import no.nav.syfo.client.pdfgen.PdfgenClient
import no.nav.syfo.journalpost.createJournalPost.onJournalRequest
import no.nav.syfo.logger
import no.nav.syfo.loggingMeta.LoggingMeta
import no.nav.syfo.loggingMeta.TrackableException
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.objectMapper
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

class LegeerklaringConsumerService(
    private val kafkaLegeerklaeringAivenConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val environmentVariables: EnvironmentVariables,
    private val legeerklaeringBucketName: String,
    private val storage: Storage,
    private val dokArkivClient: DokArkivClient,
    private val pdfgenClient: PdfgenClient,
    private val legeerklaeringVedleggBucketName: String,
    private val norskHelsenettClient: NorskHelsenettClient,
    val delayTime: Long,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val mutex = Mutex()

    companion object {
        private val log = LoggerFactory.getLogger(LegeerklaringConsumerService::class.java)
    }

    suspend fun start() =
        mutex.withLock {
            if (job != null) {
                log.warn("LegeerklaringConsumerService is allready running")
                return
            }
            job =
                scope.launch {
                    while (applicationState.ready && isActive) {
                        try {
                            kafkaLegeerklaeringAivenConsumer.subscribe(listOf(environmentVariables.legeerklaringTopic))
                            runConsumer()
                        } catch (trackableExepction: TrackableException) {
                            logger.error(
                                "Error running kafka consumer, unsubscribing and waiting 60 seconds for retry",
                                StructuredArguments.fields(trackableExepction.loggingMeta),
                                trackableExepction
                            )
                            delay(delayTime)
                        } catch (ex: Exception) {
                            logger.error(
                                "Error running kafka consumer, unsubscribing and waiting 60 seconds for retry",
                                ex
                            )
                            delay(delayTime)
                        } finally {
                            kafkaLegeerklaeringAivenConsumer.unsubscribe()
                        }
                    }
                }
        }

    suspend fun runConsumer() = coroutineScope {
        while (applicationState.ready && isActive) {
            kafkaLegeerklaeringAivenConsumer
                .poll(Duration.ofSeconds(10))
                .filter {
                    !(it.headers().any { header ->
                        header.value().contentEquals("macgyver".toByteArray())
                    })
                }
                .filter { it.value() != null }
                .forEach { consumerRecord ->
                    logger.info(
                        "Offset for topic: ${environmentVariables.legeerklaringTopic}, offset: ${consumerRecord.offset()}",
                    )
                    val legeerklaeringKafkaMessage: LegeerklaeringKafkaMessage =
                        objectMapper.readValue(consumerRecord.value())
                    val receivedLegeerklaering =
                        getLegeerklaering(
                            legeerklaeringBucketName,
                            storage,
                            legeerklaeringKafkaMessage.legeerklaeringObjectId,
                        )

                    val loggingMeta =
                        LoggingMeta(
                            mottakId = receivedLegeerklaering.navLogId,
                            orgNr = receivedLegeerklaering.legekontorOrgNr,
                            msgId = receivedLegeerklaering.msgId,
                            legeerklaeringId = receivedLegeerklaering.legeerklaering.id,
                        )

                    onJournalRequest(
                        dokArkivClient,
                        pdfgenClient,
                        legeerklaeringVedleggBucketName,
                        storage,
                        norskHelsenettClient,
                        receivedLegeerklaering,
                        legeerklaeringKafkaMessage.validationResult,
                        legeerklaeringKafkaMessage.vedlegg,
                        loggingMeta,
                        environmentVariables.cluster
                    )
                }
        }
    }

    suspend fun stop() =
        mutex.withLock {
            try {
                kafkaLegeerklaeringAivenConsumer.wakeup()
            } catch (ex: Exception) {
                log.error("Error stopping LegeerklaringConsumerService", ex)
            } finally {
                job?.cancelAndJoin()
                runCatching { kafkaLegeerklaeringAivenConsumer.close() }
                    .onFailure {
                        log.error(
                            "Error closing KafkaConsumer for LegeerklaringConsumerService",
                            it
                        )
                    }
                job = null
            }
        }
}
