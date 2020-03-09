package no.nav.syfo

import java.time.LocalDateTime
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.ForslagTilTiltak
import no.nav.syfo.model.FunksjonsOgArbeidsevne
import no.nav.syfo.model.Henvisning
import no.nav.syfo.model.Kontakt
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.Pasient
import no.nav.syfo.model.PdfModel
import no.nav.syfo.model.Plan
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Signatur
import no.nav.syfo.model.Status
import no.nav.syfo.model.Sykdomsopplysninger
import no.nav.syfo.model.ValidationResult
import org.junit.Test

internal class PdfModelTest {

    @Test
    internal fun `Creates a static pdfpayload`() {
            val legeerklaering = Legeerklaering(
                id = "12314",
                arbeidsvurderingVedSykefravaer = true,
                arbeidsavklaringspenger = true,
                yrkesrettetAttforing = false,
                uforepensjon = true,
                pasient = Pasient(
                    fornavn = "Test",
                    mellomnavn = "Testerino",
                    etternavn = "Testsen",
                    fnr = "0123456789",
                    navKontor = "NAV Stockholm",
                    adresse = "Oppdiktet veg 99",
                    postnummer = 9999,
                    poststed = "Stockholm",
                    yrke = "Taco spesialist",
                    arbeidsgiver = Arbeidsgiver(
                        navn = "NAV IKT",
                        adresse = "Sannergata 2",
                        postnummer = 557,
                        poststed = "Oslo"
                    )
                ),
                sykdomsopplysninger = Sykdomsopplysninger(
                    hoveddiagnose = Diagnose(
                        tekst = "Fysikalsk behandling/rehabilitering",
                        kode = "-57"
                    ),
                    bidiagnose = listOf(Diagnose(
                        tekst = "Engstelig for hjertesykdom",
                        kode = "K24"
                    )),
                    arbeidsuforFra = LocalDateTime.now().minusDays(3),
                    sykdomshistorie = "Tekst",
                    statusPresens = "Tekst",
                    borNavKontoretVurdereOmDetErEnYrkesskade = true,
                    yrkesSkadeDato = LocalDateTime.now().minusDays(4)
                ),
                plan = Plan(
                    utredning = null,
                    behandling = Henvisning(
                        tekst = "2 timer i uken med svømming",
                        dato = LocalDateTime.now(),
                        antattVentetIUker = 1
                    ),
                    utredningsplan = "Tekst",
                    behandlingsplan = "Tekst",
                    vurderingAvTidligerePlan = "Tekst",
                    narSporreOmNyeLegeopplysninger = "Tekst",
                    videreBehandlingIkkeAktueltGrunn = "Tekst"
                ),
                forslagTilTiltak = ForslagTilTiltak(
                    behov = true,
                    kjopAvHelsetjenester = true,
                    reisetilskudd = false,
                    aktivSykmelding = false,
                    hjelpemidlerArbeidsplassen = true,
                    arbeidsavklaringspenger = true,
                    friskmeldingTilArbeidsformidling = false,
                    andreTiltak = "Trenger taco i lunsjen",
                    naermereOpplysninger = "Tacoen må bestå av ordentlige råvarer",
                    tekst = "Pasienten har store problemer med fordøying av annen mat enn Taco"

                ),
                funksjonsOgArbeidsevne = FunksjonsOgArbeidsevne(
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
                    kanIkkeTaAnnetArbeid = "Spise annen mat enn Taco"
                ),
                prognose = Prognose(
                    vilForbedreArbeidsevne = true,
                    anslattVarighetSykdom = "1 uke",
                    anslattVarighetFunksjonsnedsetting = "2 uker",
                    anslattVarighetNedsattArbeidsevne = "4 uker"
                ),
                arsakssammenheng = "Funksjonsnedsettelsen har stor betydning for at arbeidsevnen er nedsatt",
                andreOpplysninger = "Tekst",
                kontakt = Kontakt(
                    skalKontakteBehandlendeLege = true,
                    skalKontakteArbeidsgiver = true,
                    skalKontakteBasisgruppe = false,
                    kontakteAnnenInstans = null,
                    onskesKopiAvVedtak = true
                ),
                tilbakeholdInnhold = false,
                pasientenBurdeIkkeVite = null,
                signatur = Signatur(
                    dato = LocalDateTime.now().minusDays(1),
                    navn = "Lege Legesen",
                    adresse = "Legeveien 33",
                    postnummer = "9999",
                    poststed = "Stockholm",
                    signatur = "Lege Legesen",
                    tlfNummer = "98765432"
                ),
                signaturDato = LocalDateTime.now()
            )
            val pdfPayload = PdfModel(
                legeerklaering = legeerklaering,
                validationResult = ValidationResult(
                    status = Status.INVALID, ruleHits = listOf(
                        RuleInfo(
                            ruleName = "PASIENT_YNGRE_ENN_13",
                            messageForUser = "Pasienten er under 13 år. Sykmelding kan ikke benyttes.",
                            messageForSender = "Pasienten er under 13 år. Sykmelding kan ikke benyttes.",
                            ruleStatus = Status.INVALID
                        ),
                        RuleInfo(
                            ruleName = "PASIENT_ELDRE_ENN_70",
                            messageForUser = "Sykmelding kan ikke benyttes etter at du har fylt 70 år",
                            messageForSender = "Pasienten er over 70 år. Sykmelding kan ikke benyttes.",
                            ruleStatus = Status.INVALID
                        )
                    )
                )
            )
            println(objectMapper.writeValueAsString(pdfPayload))
        }
    }
