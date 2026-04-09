package no.nav.syfo.client.pdfgenrs

import java.nio.file.Files
import java.time.LocalDateTime
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ForslagTilTiltak
import no.nav.syfo.model.FunksjonsOgArbeidsevne
import no.nav.syfo.model.Henvisning
import no.nav.syfo.model.Kontakt
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.Pasient
import no.nav.syfo.model.Plan
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.Signatur
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykdomsopplysninger
import no.nav.syfo.model.ValidationResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

internal class TypstClientTest {

    companion object {
        private val typstContainer =
            GenericContainer(DockerImageName.parse("ghcr.io/typst/typst:latest"))
                .withCommand("--version")

        private lateinit var typstBinaryPath: String
        private lateinit var templatePath: String
        private lateinit var fontPath: String

        @BeforeAll
        @JvmStatic
        fun setup() {
            typstContainer.start()

            val tempDir = Files.createTempDirectory("typst-test").toFile()

            val binaryFile = tempDir.resolve("typst")
            typstContainer.copyFileFromContainer("/bin/typst", binaryFile.absolutePath)
            binaryFile.setExecutable(true)
            typstBinaryPath = binaryFile.absolutePath

            val typstPdfDir = findTypstPdfDir()
            fontPath = "$typstPdfDir/fonts"
            templatePath = "$typstPdfDir/pale-2.typ"
        }

        private fun findTypstPdfDir(): String {
            var dir = java.io.File("").absoluteFile
            repeat(6) {
                val candidate = dir.resolve("typst-pdf")
                if (candidate.isDirectory && candidate.resolve("pale-2.typ").exists()) {
                    return candidate.absolutePath
                }
                dir = dir.parentFile ?: return@repeat
            }
            error("Could not find typst-pdf directory containing pale-2.typ")
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            typstContainer.stop()
        }
    }

    @Test
    fun `createPdf generates valid PDF bytes`() {
        val typstClient =
            TypstClient(
                typstBinaryPath = typstBinaryPath,
                templatePath = templatePath,
                fontPath = fontPath,
            )

        val payload = buildPdfrsModel()

        val pdfBytes = typstClient.createPdf(payload)

        assertTrue(pdfBytes.isNotEmpty(), "PDF output should not be empty")
        assertTrue(
            pdfBytes.size >= 5 && String(pdfBytes, 0, 5).startsWith("%PDF"),
            "Output should start with %PDF header",
        )
    }

    private fun buildPdfrsModel(): PdfrsModel =
        PdfrsModel(
            legeerklaering =
                Legeerklaering(
                    id = "12314",
                    arbeidsvurderingVedSykefravaer = true,
                    arbeidsavklaringspenger = true,
                    yrkesrettetAttforing = false,
                    uforepensjon = true,
                    pasient =
                        Pasient(
                            fornavn = "Test",
                            mellomnavn = "Testerino",
                            etternavn = "Testsen",
                            fnr = "0123456789",
                            navKontor = "NAV Stockholm",
                            adresse = "Oppdiktet veg 99",
                            postnummer = 9999,
                            poststed = "Stockholm",
                            yrke = "Taco spesialist",
                            arbeidsgiver =
                                Arbeidsgiver(
                                    navn = "NAV IKT",
                                    adresse = "Sannergata 2",
                                    postnummer = 557,
                                    poststed = "Oslo",
                                ),
                        ),
                    sykdomsopplysninger =
                        Sykdomsopplysninger(
                            hoveddiagnose =
                                Diagnose(
                                    tekst = "Fysikalsk behandling/rehabilitering",
                                    kode = "-57",
                                ),
                            bidiagnose =
                                listOf(
                                    Diagnose(
                                        tekst = "Engstelig for hjertesykdom",
                                        kode = "K24",
                                    ),
                                ),
                            arbeidsuforFra = LocalDateTime.now().minusDays(3),
                            sykdomshistorie = "Tekst",
                            statusPresens = "Tekst",
                            borNavKontoretVurdereOmDetErEnYrkesskade = true,
                            yrkesSkadeDato = LocalDateTime.now().minusDays(4),
                        ),
                    plan =
                        Plan(
                            utredning = null,
                            behandling =
                                Henvisning(
                                    tekst = "2 timer i uken med svomming",
                                    dato = LocalDateTime.now(),
                                    antattVentetIUker = 1,
                                ),
                            utredningsplan = "Utredningsplan tekst",
                            behandlingsplan = "Behandlingsplan tekst",
                            vurderingAvTidligerePlan = "Vurdering tekst",
                            narSporreOmNyeLegeopplysninger = "Sporre tekst",
                            videreBehandlingIkkeAktueltGrunn = null,
                        ),
                    forslagTilTiltak =
                        ForslagTilTiltak(
                            behov = true,
                            kjopAvHelsetjenester = true,
                            reisetilskudd = false,
                            aktivSykmelding = false,
                            hjelpemidlerArbeidsplassen = true,
                            arbeidsavklaringspenger = true,
                            friskmeldingTilArbeidsformidling = false,
                            andreTiltak = "Trenger taco i lunsjen",
                            naermereOpplysninger = "Tacoen maa bestaa av ordentlige raavarer",
                            tekst = "Pasienten har store problemer med fordoyelse",
                        ),
                    funksjonsOgArbeidsevne =
                        FunksjonsOgArbeidsevne(
                            vurderingFunksjonsevne = "Kan ikke spise annet enn Taco",
                            inntektsgivendeArbeid = false,
                            hjemmearbeidende = false,
                            student = false,
                            annetArbeid = "Reisende taco tester",
                            kravTilArbeid = "Kun taco i kantina",
                            kanGjenopptaTidligereArbeid = true,
                            kanGjenopptaTidligereArbeidNa = true,
                            kanGjenopptaTidligereArbeidEtterBehandling = true,
                            kanTaAnnetArbeid = true,
                            kanTaAnnetArbeidNa = true,
                            kanTaAnnetArbeidEtterBehandling = true,
                            kanIkkeGjenopptaNaverendeArbeid = null,
                            kanIkkeTaAnnetArbeid = null,
                        ),
                    prognose =
                        Prognose(
                            vilForbedreArbeidsevne = true,
                            anslattVarighetSykdom = "1 uke",
                            anslattVarighetFunksjonsnedsetting = "2 uker",
                            anslattVarighetNedsattArbeidsevne = "4 uker",
                        ),
                    arsakssammenheng = "Funksjonsnedsettelsen pavirker arbeidsevnen",
                    andreOpplysninger = "Tekst",
                    kontakt =
                        Kontakt(
                            skalKontakteBehandlendeLege = true,
                            skalKontakteArbeidsgiver = true,
                            skalKontakteBasisgruppe = false,
                            kontakteAnnenInstans = null,
                            onskesKopiAvVedtak = true,
                        ),
                    tilbakeholdInnhold = false,
                    pasientenBurdeIkkeVite = null,
                    signatur =
                        Signatur(
                            dato = LocalDateTime.now().minusDays(1),
                            navn = "Lege Legesen",
                            adresse = "Legeveien 33",
                            postnummer = "9999",
                            poststed = "Stockholm",
                            signatur = "Lege Legesen",
                            tlfNummer = "98765432",
                        ),
                    signaturDato = LocalDateTime.now(),
                ),
            validationResult =
                ValidationResult(
                    status = Status.OK,
                    ruleHits = emptyList(),
                ),
            mottattDato = LocalDateTime.now(),
        )
}
