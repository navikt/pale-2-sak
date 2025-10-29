package no.nav.syfo

import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.isSuccess
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.prometheus.client.hotspot.DefaultExports
import java.util.concurrent.TimeUnit
import kotlin.String
import kotlin.collections.set
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.accessToken.AccessTokenClient
import no.nav.syfo.client.dokArkivClient.DokArkivClient
import no.nav.syfo.client.norskHelsenettClient.NorskHelsenettClient
import no.nav.syfo.client.pdfgen.PdfgenClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.legeerklaring.LegeerklaringConsumerService
import no.nav.syfo.nais.isalive.naisIsAliveRoute
import no.nav.syfo.nais.isready.naisIsReadyRoute
import no.nav.syfo.nais.prometheus.naisPrometheusRoute
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
        install(HttpRequestRetry) {
            exponentialDelay(2.0, baseDelayMs = 1000, maxDelayMs = 20_000)
            retryOnExceptionIf(5) { request, throwable ->
                secureLogger.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(5) { request, response -> !response.status.isSuccess() }
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

    val paleVedleggStorage: Storage = StorageOptions.newBuilder().build().service

    val aivenConsumerProperties =
        KafkaUtils.getAivenKafkaConfig()
            .toConsumerConfig(
                "${environmentVariables.applicationName}-consumer",
                valueDeserializer = StringDeserializer::class,
            )
            .also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none" }
    val kafkaLegeerklaeringAivenConsumer = KafkaConsumer<String, String>(aivenConsumerProperties)

    val legeerklaringConsumerService =
        LegeerklaringConsumerService(
            kafkaLegeerklaeringAivenConsumer,
            applicationState,
            environmentVariables,
            environmentVariables.legeerklaeringBucketName,
            storage = paleVedleggStorage,
            dokArkivClient,
            pdfgenClient,
            environmentVariables.paleVedleggBucketName,
            norskHelsenettClient,
            60_000,
        )
    monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        applicationState.alive = true
        launch { legeerklaringConsumerService.start() }
    }

    monitor.subscribe(ApplicationStopping) {
        runBlocking { legeerklaringConsumerService.stop() }
        applicationState.ready = false
        applicationState.alive = false
    }
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

class ServiceUnavailableException(message: String?) : Exception(message)
