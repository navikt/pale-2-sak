package no.nav.syfo

import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.client.DokArkivClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.PdfgenClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.service.BucketService
import no.nav.syfo.service.JournalService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.time.Duration

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    factory.setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(12_000_000).build())
}

val log: Logger = LoggerFactory.getLogger("no.nav.no.nav.syfo.pale2sak")

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    DefaultExports.initialize()

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 120_000
            connectTimeoutMillis = 40_000
            requestTimeoutMillis = 40_000
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn("Retrying for statuscode ${response.status.value}, for url ${request.url}")
                    true
                } else {
                    false
                }
            }
        }
    }

    val httpClient = HttpClient(Apache, config)

    val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, env.clientId, env.clientSecret, httpClient)
    val dokArkivClient = DokArkivClient(env.dokArkivUrl, accessTokenClient, env.dokArkivScope, httpClient)
    val pdfgenClient = PdfgenClient(env.pdfgen, httpClient)
    val norskHelsenettClient = NorskHelsenettClient(env.norskHelsenettEndpointURL, accessTokenClient, env.helsenettproxyScope, httpClient)

    val paleVedleggStorageCredentials: Credentials = GoogleCredentials.fromStream(FileInputStream("/var/run/secrets/pale2-google-creds.json"))
    val paleVedleggStorage: Storage = StorageOptions.newBuilder().setCredentials(paleVedleggStorageCredentials).build().service
    val paleBucketService = BucketService(
        vedleggBucketName = env.paleVedleggBucketName,
        legeerklaeringBucketName = env.legeerklaeringBucketName,
        storage = paleVedleggStorage,
    )

    val journalService = JournalService(dokArkivClient, pdfgenClient, paleBucketService, norskHelsenettClient)

    launchListeners(env, applicationState, paleBucketService, journalService)

    applicationServer.start()
}

@DelicateCoroutinesApi
fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
        } finally {
            applicationState.ready = false
            applicationState.alive = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    bucketService: BucketService,
    journalService: JournalService,
) {
    createListener(applicationState) {
        val aivenConsumerProperties = KafkaUtils.getAivenKafkaConfig()
            .toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
            .also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none" }
        val kafkaLegeerklaeringAivenConsumer = KafkaConsumer<String, String>(aivenConsumerProperties)
        kafkaLegeerklaeringAivenConsumer.subscribe(listOf(env.legeerklaringTopic))

        blockingApplicationLogic(
            kafkaLegeerklaeringAivenConsumer,
            bucketService,
            applicationState,
            journalService,
            env,
        )
    }
}

suspend fun blockingApplicationLogic(
    kafkaLegeerklaeringAivenConsumer: KafkaConsumer<String, String>,
    bucketService: BucketService,
    applicationState: ApplicationState,
    journalService: JournalService,
    env: Environment,
) {
    while (applicationState.ready) {
        kafkaLegeerklaeringAivenConsumer.poll(Duration.ofSeconds(10))
            .filter { !(it.headers().any { header -> header.value().contentEquals("macgyver".toByteArray()) }) }
            .filter { it.value() != null }
            .forEach { consumerRecord ->
                log.info("Offset for topic: ${env.legeerklaringTopic}, offset: ${consumerRecord.offset()}")
                val legeerklaeringKafkaMessage: LegeerklaeringKafkaMessage = objectMapper.readValue(consumerRecord.value())
                val receivedLegeerklaering = bucketService.getLegeerklaeringFromBucket(legeerklaeringKafkaMessage.legeerklaeringObjectId)

                val loggingMeta = LoggingMeta(
                    mottakId = receivedLegeerklaering.navLogId,
                    orgNr = receivedLegeerklaering.legekontorOrgNr,
                    msgId = receivedLegeerklaering.msgId,
                    legeerklaeringId = receivedLegeerklaering.legeerklaering.id,
                )

                journalService.onJournalRequest(
                    receivedLegeerklaering,
                    legeerklaeringKafkaMessage.validationResult,
                    legeerklaeringKafkaMessage.vedlegg,
                    loggingMeta,
                )
            }
    }
}
