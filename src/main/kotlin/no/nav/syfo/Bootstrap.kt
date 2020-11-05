package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.time.Duration
import java.util.Properties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.client.SakClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.model.LegeerklaeringSak
import no.nav.syfo.service.JournalService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.getFileAsString
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

val log: Logger = LoggerFactory.getLogger("no.nav.no.nav.syfo.pale2sak")

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val vaultSecrets = VaultSecrets(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username")
    )
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    DefaultExports.initialize()

    val httpClient = HttpClient(Apache) {
        engine {
            socketTimeout = 40_000
            connectTimeout = 40_000
            connectionRequestTimeout = 40_000
        }
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }

    val stsClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    val sakClient = SakClient(env.opprettSakUrl, stsClient, httpClient)
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, stsClient, httpClient)
    val pdfgenClient = PdfgenClient(env.pdfgen, httpClient)

    val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
    kafkaBaseConfig["auto.offset.reset"] = "none"
    val consumerConfig = kafkaBaseConfig.toConsumerConfig(
        "${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class
    )

    val journalService = JournalService(sakClient, dokArkivClient, pdfgenClient)

    launchListeners(env, applicationState, consumerConfig, journalService)
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
        } finally {
            applicationState.alive = false
        }
    }

@KtorExperimentalAPI
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    consumerProperties: Properties,
    journalService: JournalService
) {
    createListener(applicationState) {
        val kafkaLegeerklaeringSakconsumer = KafkaConsumer<String, String>(consumerProperties)
        kafkaLegeerklaeringSakconsumer.subscribe(listOf(env.pale2SakTopic))
        applicationState.ready = true

        blockingApplicationLogic(
            kafkaLegeerklaeringSakconsumer,
            applicationState,
            journalService,
            env
        )
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    kafkaLegeerklaeringSakconsumer: KafkaConsumer<String, String>,
    applicationState: ApplicationState,
    journalService: JournalService,
    env: Environment
) {
    while (applicationState.ready) {
        kafkaLegeerklaeringSakconsumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            log.info("Offset for topic: ${env.pale2SakTopic}, offset: ${consumerRecord.offset()}")

            val legeerklaeringSak: LegeerklaeringSak = objectMapper.readValue(consumerRecord.value())

            val loggingMeta = LoggingMeta(
                mottakId = legeerklaeringSak.receivedLegeerklaering.navLogId,
                orgNr = legeerklaeringSak.receivedLegeerklaering.legekontorOrgNr,
                msgId = legeerklaeringSak.receivedLegeerklaering.msgId,
                legeerklaeringId = legeerklaeringSak.receivedLegeerklaering.legeerklaering.id
            )

            if (legeerklaeringSak.receivedLegeerklaering.mottattDato.isBefore(LocalDate.of(2020, 11, 5).atStartOfDay())) {
                log.info("Behandler ikke gammel legeerklæring {}", fields(loggingMeta))
            } else {
                journalService.onJournalRequest(
                    legeerklaeringSak.receivedLegeerklaering,
                    legeerklaeringSak.validationResult,
                    loggingMeta
                )
            }
        }

        delay(1)
    }
}
