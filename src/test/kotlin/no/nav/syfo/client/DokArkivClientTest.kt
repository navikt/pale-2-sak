package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.model.JournalpostRequest
import no.nav.syfo.model.JournalpostResponse
import no.nav.syfo.model.VedleggMessage
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

internal class DokArkivClientTest {
    private val accessTokenClient = mockk<AccessTokenClient>()
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
                    call.request.header("Nav-Callid") == "NY" -> call.respond(
                        HttpStatusCode.Created,
                        JournalpostResponse(
                            emptyList(), "nyJpId", true, null, null
                        )
                    )
                    call.request.header("Nav-Callid") == "DUPLIKAT" -> call.respond(
                        HttpStatusCode.Conflict,
                        JournalpostResponse(
                            emptyList(), "eksisterendeJpId", true, null, null
                        )
                    )
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }.start()

    private val dokArkivClient = DokArkivClient("$mockHttpServerUrl/dokarkiv", accessTokenClient, "scope", httpClient)

    @After
    fun after() {
        mockServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
    }

    @Before
    fun before() {
        coEvery { accessTokenClient.getAccessToken(any()) } returns "token"
    }

    @Test
    internal fun `Happy-case`() {
        var jpResponse: JournalpostResponse?
        runBlocking {
            jpResponse = dokArkivClient.createJournalpost(
                JournalpostRequest(
                    dokumenter = emptyList(),
                    eksternReferanseId = "NY"
                ),
                loggingMetadata
            )
        }

        jpResponse?.journalpostId shouldBeEqualTo "nyJpId"
    }

    @Test
    internal fun `Feiler ikke ved duplikat`() {
        var jpResponse: JournalpostResponse?
        runBlocking {
            jpResponse = dokArkivClient.createJournalpost(
                JournalpostRequest(
                    dokumenter = emptyList(),
                    eksternReferanseId = "DUPLIKAT"
                ),
                loggingMetadata
            )
        }

        jpResponse?.journalpostId shouldBeEqualTo "eksisterendeJpId"
    }

    @Test
    internal fun `Returnerer samme vedlegg hvis vedlegget er PDF`() {
        val vedleggMessage: VedleggMessage = objectMapper.readValue(DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_pdf.json"))
        val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

        val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

        oppdatertVedlegg shouldBeEqualTo gosysVedlegg
    }

    @Test
    internal fun `Konverterer til PDF hvis vedlegget ikke er PDF`() {
        val vedleggMessage: VedleggMessage = objectMapper.readValue(DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_bilde.json"))
        val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

        val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

        oppdatertVedlegg shouldNotBeEqualTo gosysVedlegg
        oppdatertVedlegg.contentType shouldBeEqualTo "application/pdf"
        oppdatertVedlegg.description shouldBeEqualTo vedleggMessage.vedlegg.description
    }
}
