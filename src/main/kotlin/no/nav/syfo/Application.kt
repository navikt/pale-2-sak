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
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.network.sockets.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.prometheus.client.hotspot.DefaultExports
import java.io.FileInputStream
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.bucket.getLegeerklaering.getLegeerklaering
import no.nav.syfo.client.accessToken.AccessTokenClient
import no.nav.syfo.client.dokArkivClient.DokArkivClient
import no.nav.syfo.client.norskHelsenettClient.NorskHelsenettClient
import no.nav.syfo.client.pdfgen.PdfgenClient
import no.nav.syfo.journalpost.createJournalPost.onJournalRequest
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.model.kafka.LegeerklaeringKafkaMessage
import no.nav.syfo.nais.isalive.naisIsAliveRoute
import no.nav.syfo.nais.isready.naisIsReadyRoute
import no.nav.syfo.nais.prometheus.naisPrometheusRoute
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        factory.setStreamReadConstraints(
            StreamReadConstraints.builder().maxStringLength(50_000_000).build(),
        )
    }

val logger: Logger = LoggerFactory.getLogger("no.nav.syfo.pale2sak")
val secureLogger: Logger = LoggerFactory.getLogger("securelog")

fun main() {
    val embeddedServer =
        embeddedServer(
            Netty,
            port = EnvironmentVariables().applicationPort,
            module = Application::module,
        )
    Runtime.getRuntime()
        .addShutdownHook(
            Thread {
                embeddedServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
            },
        )
    embeddedServer.start(true)
}

@OptIn(DelicateCoroutinesApi::class)
fun Application.module() {
    val environmentVariables = EnvironmentVariables()
    val applicationState = ApplicationState()

    environment.monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
        applicationState.alive = false
    }

    configureRouting(applicationState = applicationState)

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
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
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
                secureLogger.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    secureLogger.warn(
                        "Retrying for statuscode ${response.status.value}, for url ${request.url}",
                    )
                    true
                } else {
                    false
                }
            }
        }
    }

    val httpClient = HttpClient(Apache, config)

    val accessTokenClient =
        AccessTokenClient(
            environmentVariables.aadAccessTokenUrl,
            environmentVariables.clientId,
            environmentVariables.clientSecret,
            httpClient,
        )
    val dokArkivClient =
        DokArkivClient(
            environmentVariables.dokArkivUrl,
            accessTokenClient,
            environmentVariables.dokArkivScope,
            httpClient,
        )
    val pdfgenClient = PdfgenClient(environmentVariables.pdfgen, httpClient)
    val norskHelsenettClient =
        NorskHelsenettClient(
            environmentVariables.norskHelsenettEndpointURL,
            accessTokenClient,
            environmentVariables.helsenettproxyScope,
            httpClient,
        )

    val paleVedleggStorageCredentials: Credentials =
        GoogleCredentials.fromStream(FileInputStream("/var/run/secrets/pale2-google-creds.json"))
    val paleVedleggStorage: Storage =
        StorageOptions.newBuilder().setCredentials(paleVedleggStorageCredentials).build().service

    launchListeners(
        environmentVariables,
        applicationState,
        environmentVariables.legeerklaeringBucketName,
        paleVedleggStorage,
        dokArkivClient,
        pdfgenClient,
        environmentVariables.paleVedleggBucketName,
        norskHelsenettClient,
    )
}

fun Application.configureRouting(applicationState: ApplicationState) {
    routing {
        naisIsAliveRoute(applicationState)
        naisIsReadyRoute(applicationState)
        naisPrometheusRoute()
    }
}

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = true,
)

@DelicateCoroutinesApi
fun createListener(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            logger.error(
                "En uhåndtert feil oppstod, applikasjonen restarter {}",
                StructuredArguments.fields(e.loggingMeta),
                e.cause,
            )
        } finally {
            applicationState.ready = false
            applicationState.alive = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    environmentVariables: EnvironmentVariables,
    applicationState: ApplicationState,
    legeerklaeringBucketName: String,
    storage: Storage,
    dokArkivClient: DokArkivClient,
    pdfgenClient: PdfgenClient,
    legeerklaeringVedleggBucketName: String,
    norskHelsenettClient: NorskHelsenettClient,
) {
    createListener(applicationState) {
        val aivenConsumerProperties =
            KafkaUtils.getAivenKafkaConfig()
                .toConsumerConfig(
                    "${environmentVariables.applicationName}-consumer",
                    valueDeserializer = StringDeserializer::class,
                )
                .also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none" }
        val kafkaLegeerklaeringAivenConsumer =
            KafkaConsumer<String, String>(aivenConsumerProperties)
        kafkaLegeerklaeringAivenConsumer.subscribe(listOf(environmentVariables.legeerklaringTopic))

        blockingApplicationLogic(
            kafkaLegeerklaeringAivenConsumer,
            applicationState,
            environmentVariables,
            legeerklaeringBucketName,
            storage,
            dokArkivClient,
            pdfgenClient,
            legeerklaeringVedleggBucketName,
            norskHelsenettClient,
        )
    }
}

suspend fun blockingApplicationLogic(
    kafkaLegeerklaeringAivenConsumer: KafkaConsumer<String, String>,
    applicationState: ApplicationState,
    environmentVariables: EnvironmentVariables,
    legeerklaeringBucketName: String,
    storage: Storage,
    dokArkivClient: DokArkivClient,
    pdfgenClient: PdfgenClient,
    legeerklaeringVedleggBucketName: String,
    norskHelsenettClient: NorskHelsenettClient,
) {
    while (applicationState.ready) {
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
                )
            }
    }
}

class ServiceUnavailableException(message: String?) : Exception(message)
