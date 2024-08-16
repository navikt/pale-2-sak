package no.nav.syfo.model

import java.time.LocalDateTime

data class Legeerklaering(
    val id: String,
    val arbeidsvurderingVedSykefravaer: Boolean,
    val arbeidsavklaringspenger: Boolean,
    val yrkesrettetAttforing: Boolean,
    val uforepensjon: Boolean,
    val pasient: Pasient,
    val sykdomsopplysninger: Sykdomsopplysninger,
    val plan: Plan?,
    val forslagTilTiltak: ForslagTilTiltak,
    val funksjonsOgArbeidsevne: FunksjonsOgArbeidsevne,
    val prognose: Prognose,
    val arsakssammenheng: String?,
    val andreOpplysninger: String?,
    val kontakt: Kontakt,
    val pasientenBurdeIkkeVite: String?,
    val tilbakeholdInnhold: Boolean,
    val signatur: Signatur,
    val signaturDato: LocalDateTime
)

data class Plan(
    val utredning: Henvisning?,
    val behandling: Henvisning?,
    val utredningsplan: String?,
    val behandlingsplan: String?,
    val vurderingAvTidligerePlan: String?,
    val narSporreOmNyeLegeopplysninger: String?,
    val videreBehandlingIkkeAktueltGrunn: String?
)

data class Pasient(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val fnr: String,
    val navKontor: String?,
    val adresse: String?,
    val postnummer: Int?,
    val poststed: String?,
    val yrke: String?,
    val arbeidsgiver: Arbeidsgiver
)

data class Arbeidsgiver(
    val navn: String?,
    val adresse: String?,
    val postnummer: Int?,
    val poststed: String?
)

data class Henvisning(val tekst: String, val dato: LocalDateTime, val antattVentetIUker: Int)

data class Sykdomsopplysninger(
    val hoveddiagnose: Diagnose?,
    val bidiagnose: List<Diagnose?>,
    val arbeidsuforFra: LocalDateTime?,
    val sykdomshistorie: String,
    val statusPresens: String,
    val borNavKontoretVurdereOmDetErEnYrkesskade: Boolean,
    val yrkesSkadeDato: LocalDateTime?
)

data class Diagnose(val tekst: String?, val kode: String?)

data class ForslagTilTiltak(
    val behov: Boolean,
    val kjopAvHelsetjenester: Boolean,
    val reisetilskudd: Boolean,
    val aktivSykmelding: Boolean,
    val hjelpemidlerArbeidsplassen: Boolean,
    val arbeidsavklaringspenger: Boolean,
    val friskmeldingTilArbeidsformidling: Boolean,
    val andreTiltak: String?,
    val naermereOpplysninger: String,
    val tekst: String
)

data class FunksjonsOgArbeidsevne(
    val vurderingFunksjonsevne: String?,
    val inntektsgivendeArbeid: Boolean,
    val hjemmearbeidende: Boolean,
    val student: Boolean,
    val annetArbeid: String,
    val kravTilArbeid: String?,
    val kanGjenopptaTidligereArbeid: Boolean,
    val kanGjenopptaTidligereArbeidNa: Boolean,
    val kanGjenopptaTidligereArbeidEtterBehandling: Boolean,
    val kanIkkeGjenopptaNaverendeArbeid: String?,
    val kanTaAnnetArbeid: Boolean,
    val kanTaAnnetArbeidNa: Boolean,
    val kanTaAnnetArbeidEtterBehandling: Boolean,
    val kanIkkeTaAnnetArbeid: String?
)

data class Prognose(
    val vilForbedreArbeidsevne: Boolean,
    val anslattVarighetSykdom: String?,
    val anslattVarighetFunksjonsnedsetting: String?,
    val anslattVarighetNedsattArbeidsevne: String?
)

data class Kontakt(
    val skalKontakteBehandlendeLege: Boolean,
    val skalKontakteArbeidsgiver: Boolean,
    val skalKontakteBasisgruppe: Boolean,
    val kontakteAnnenInstans: String?,
    val onskesKopiAvVedtak: Boolean
)

data class Signatur(
    val dato: LocalDateTime,
    val navn: String?,
    val adresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val signatur: String?,
    val tlfNummer: String?
)
