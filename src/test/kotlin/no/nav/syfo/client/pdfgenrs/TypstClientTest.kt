package no.nav.syfo.client.pdfgenrs

import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import javax.imageio.ImageIO
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
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Signatur
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykdomsopplysninger
import no.nav.syfo.model.ValidationResult
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

internal class TypstClientTest {

    private val typstBinaryPath: String = System.getProperty("typst.binary.path", "")
    private val projectDir: String = System.getProperty("project.dir", "")

    private fun buildTypstClient() =
        TypstClient(
            typstBinaryPath = typstBinaryPath,
            templatePath = "$projectDir/typst-pdf/pale-2.typ",
            fontPath = "$projectDir/typst-pdf/fonts",
        )

    private fun buildLegeerklaering() =
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
                    sykdomshistorie = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.",
                    statusPresens = "Pasienten er i god form.",
                    borNavKontoretVurdereOmDetErEnYrkesskade = true,
                    yrkesSkadeDato = LocalDateTime.now().minusDays(4),
                ),
            plan =
                Plan(
                    utredning = null,
                    behandling =
                        Henvisning(
                            tekst = "2 timer i uken med svømming",
                            dato = LocalDateTime.now(),
                            antattVentetIUker = 1,
                        ),
                    utredningsplan = "Planlagt utredning",
                    behandlingsplan = "Planlagt behandling",
                    vurderingAvTidligerePlan = "Vurdering",
                    narSporreOmNyeLegeopplysninger = "Om 3 måneder",
                    videreBehandlingIkkeAktueltGrunn = "",
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
                    naermereOpplysninger = "Tacoen må bestå av ordentlige råvarer",
                    tekst = "Pasienten har store problemer med fordøying av annen mat enn Taco",
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
                    kanIkkeGjenopptaNaverendeArbeid = "Spise annen mat enn Taco",
                    kanIkkeTaAnnetArbeid = "Spise annen mat enn Taco",
                ),
            prognose =
                Prognose(
                    vilForbedreArbeidsevne = true,
                    anslattVarighetSykdom = "1 uke",
                    anslattVarighetFunksjonsnedsetting = "2 uker",
                    anslattVarighetNedsattArbeidsevne = "4 uker",
                ),
            arsakssammenheng =
                "Funksjonsnedsettelsen har stor betydning for at arbeidsevnen er nedsatt",
            andreOpplysninger = "Ingen andre opplysninger",
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
        )

    private fun saveScreenshot(pdfBytes: ByteArray, filename: String) {
        val screenshotDir = File("$projectDir/build/reports/tests/pdfscreenshots")
        screenshotDir.mkdirs()
        PDDocument.load(pdfBytes).use { document ->
            val renderer = PDFRenderer(document)
            for (pageIndex in 0 until document.numberOfPages) {
                val image: BufferedImage = renderer.renderImageWithDPI(pageIndex, 150f)
                val screenshotFile = File(screenshotDir, "${filename}-page${pageIndex + 1}.png")
                ImageIO.write(image, "PNG", screenshotFile)
                println("PDF screenshot saved to: ${screenshotFile.absolutePath}")
            }
        }
    }

    @Test
    fun `creates a valid pdf for an OK legeerklaering`() {
        assumeTrue(
            File(typstBinaryPath).exists(),
            "Typst binary not available at '$typstBinaryPath', skipping test",
        )

        val payload =
            PdfrsModel(
                legeerklaering = buildLegeerklaering(),
                validationResult = ValidationResult(status = Status.OK, ruleHits = emptyList()),
                mottattDato = LocalDateTime.now(),
            )

        val pdfBytes = buildTypstClient().createPdf(payload)

        assertTrue(pdfBytes.isNotEmpty(), "PDF should not be empty")
        PDDocument.load(pdfBytes).use { document ->
            assertNotNull(document)
            assertTrue(document.numberOfPages > 0, "PDF should have at least one page")
        }
        saveScreenshot(pdfBytes, "legeerklaering-ok")
    }

    @Test
    fun `creates a valid pdf for an INVALID legeerklaering with rule hits`() {
        assumeTrue(
            File(typstBinaryPath).exists(),
            "Typst binary not available at '$typstBinaryPath', skipping test",
        )

        val payload =
            PdfrsModel(
                legeerklaering = buildLegeerklaering(),
                validationResult =
                    ValidationResult(
                        status = Status.INVALID,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName = "BEHANDLER_MANGLER_AUTORISASJON_I_HPR",
                                    messageForSender =
                                        "Den som skrev legeerklæringen manglet autorisasjon.",
                                    messageForUser =
                                        "Behandler har ikke gyldig autorisasjon i HPR",
                                    ruleStatus = Status.INVALID,
                                ),
                                RuleInfo(
                                    ruleName = "BEHANDLER_SUSPENDERT",
                                    messageForSender =
                                        "Den som sendte legeerklæringen har mistet retten til å skrive legeerklæringer.",
                                    messageForUser =
                                        "Behandler er suspendert av NAV på konsultasjonstidspunkt",
                                    ruleStatus = Status.INVALID,
                                ),
                            ),
                    ),
                mottattDato = LocalDateTime.now(),
            )

        val pdfBytes = buildTypstClient().createPdf(payload)

        assertTrue(pdfBytes.isNotEmpty(), "PDF should not be empty")
        PDDocument.load(pdfBytes).use { document ->
            assertNotNull(document)
            assertTrue(document.numberOfPages > 0, "PDF should have at least one page")
        }
        saveScreenshot(pdfBytes, "legeerklaering-invalid")
    }
}
