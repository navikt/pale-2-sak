package no.nav.syfo.client.dokArkivClient

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.accessToken.AccessTokenClient
import no.nav.syfo.journalpost.createJournalPost.JournalpostRequest
import no.nav.syfo.journalpost.createJournalPost.JournalpostResponse
import no.nav.syfo.journalpost.createJournalPost.VedleggMessage
import no.nav.syfo.jsonMapper
import no.nav.syfo.logger
import no.nav.syfo.loggingMeta.LoggingMeta
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue

internal class DokArkivClientTest {
    private val accessTokenClient = mockk<AccessTokenClient>()
    private val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) { jackson {} }
            install(HttpRequestRetry) {
                constantDelay(100, 0, false)
                retryOnExceptionIf(3) { request, throwable ->
                    logger.warn("Caught exception ${throwable.message}, for url ${request.url}")
                    true
                }
                retryIf(maxRetries) { request, response ->
                    if (response.status.value.let { it in 500..599 }) {
                        logger.warn(
                            "Retrying for statuscode ${response.status.value}, for url ${request.url}"
                        )
                        true
                    } else {
                        false
                    }
                }
            }
            expectSuccess = false
        }
    private val loggingMetadata = LoggingMeta("mottakId", "orgnur", "msgId", "legeerklæringId")

    private val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    private val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    private val mockServer =
        embeddedServer(Netty, mockHttpServerPort) {
                install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { jackson {} }
                routing {
                    post("/dokarkiv") {
                        when {
                            call.request.header("Nav-Callid") == "NY" ->
                                call.respond(
                                    HttpStatusCode.Created,
                                    JournalpostResponse(emptyList(), "nyJpId", true, null, null),
                                )
                            call.request.header("Nav-Callid") == "DUPLIKAT" ->
                                call.respond(
                                    HttpStatusCode.Conflict,
                                    JournalpostResponse(
                                        emptyList(),
                                        "eksisterendeJpId",
                                        true,
                                        null,
                                        null,
                                    ),
                                )
                            else -> call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }
            .start()

    private val dokArkivClient =
        DokArkivClient("$mockHttpServerUrl/dokarkiv", accessTokenClient, "scope", httpClient)

    @AfterEach
    fun after() {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    @BeforeEach
    fun before() {
        coEvery { accessTokenClient.getAccessToken(any(), any()) } returns "token"
    }

    @Test
    internal fun `Happy-case`() {
        var jpResponse: JournalpostResponse?
        runBlocking {
            jpResponse =
                dokArkivClient.createJournalpost(
                    JournalpostRequest(dokumenter = emptyList(), eksternReferanseId = "NY"),
                    loggingMetadata,
                )
        }

        assertEquals("nyJpId", jpResponse?.journalpostId)
    }

    @Test
    internal fun `Feiler ikke ved duplikat`() {
        var jpResponse: JournalpostResponse?
        runBlocking {
            jpResponse =
                dokArkivClient.createJournalpost(
                    JournalpostRequest(dokumenter = emptyList(), eksternReferanseId = "DUPLIKAT"),
                    loggingMetadata,
                )
        }

        assertEquals("eksisterendeJpId", jpResponse?.journalpostId)
    }

    @Test
    internal fun `Returnerer samme vedlegg hvis vedlegget er PDF`() {
        val vedleggMessage: VedleggMessage =
            jsonMapper.readValue(
                DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_pdf.json")!!
            )
        val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

        val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

        assertEquals(gosysVedlegg, oppdatertVedlegg)
    }

    @Test
    internal fun `Konverterer til PDF hvis vedlegget ikke er PDF`() {
        val vedleggMessage: VedleggMessage =
            jsonMapper.readValue(
                DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_bilde.json")!!
            )
        val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

        val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

        assertNotEquals(gosysVedlegg, oppdatertVedlegg)
        assertEquals("application/pdf", oppdatertVedlegg!!.contentType)
        assertEquals(vedleggMessage.vedlegg.description, oppdatertVedlegg.description)
    }

    @Test
    internal fun `Ignorerer vedlegg av ugyldig type`() {
        val vedleggMessage: VedleggMessage =
            jsonMapper.readValue(
                DokArkivClientTest::class.java.getResourceAsStream("/vedlegg_html.json")!!
            )
        val gosysVedlegg = toGosysVedlegg(vedleggMessage.vedlegg)

        val oppdatertVedlegg = vedleggToPDF(gosysVedlegg)

        assertEquals(null, oppdatertVedlegg)
    }
}
