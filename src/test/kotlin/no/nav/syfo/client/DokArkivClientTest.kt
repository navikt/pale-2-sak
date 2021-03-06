package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.junit.After
import org.junit.Before
import org.junit.Test

@KtorExperimentalAPI
internal class DokArkivClientTest {
    private val stsOidcClientMock = mockk<StsOidcClient>()
    private val httpClient = HttpClient(Apache) {
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
    private val loggingMetadata = LoggingMeta("mottakId", "orgnur", "msgId", "legeerklæringId")

    private val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    private val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    private val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {}
        }
        routing {
            post("/dokarkiv") {
                when {
                    call.request.header("Nav-Callid") == "NY" -> call.respond(HttpStatusCode.Created, JournalpostResponse(
                        emptyList(), "nyJpId", true, null, null
                    ))
                    call.request.header("Nav-Callid") == "DUPLIKAT" -> call.respond(HttpStatusCode.Conflict, JournalpostResponse(
                        emptyList(), "eksisterendeJpId", true, null, null
                    ))
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }.start()

    private val dokArkivClient = DokArkivClient("$mockHttpServerUrl/dokarkiv", stsOidcClientMock, httpClient)

    @After
    fun after() {
        mockServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
    }

    @Before
    fun before() {
        coEvery { stsOidcClientMock.oidcToken() } returns OidcToken("token", "type", 300L)
    }

    @Test
    internal fun `Happy-case`() {
        var jpResponse: JournalpostResponse? = null
        runBlocking {
            jpResponse = dokArkivClient.createJournalpost(JournalpostRequest(dokumenter = emptyList(), eksternReferanseId = "NY"), loggingMetadata)
        }

        jpResponse?.journalpostId shouldEqual "nyJpId"
    }

    @Test
    internal fun `Feiler ikke ved duplikat`() {
        var jpResponse: JournalpostResponse? = null
        runBlocking {
            jpResponse = dokArkivClient.createJournalpost(JournalpostRequest(dokumenter = emptyList(), eksternReferanseId = "DUPLIKAT"), loggingMetadata)
        }

        jpResponse?.journalpostId shouldEqual "eksisterendeJpId"
    }
}
