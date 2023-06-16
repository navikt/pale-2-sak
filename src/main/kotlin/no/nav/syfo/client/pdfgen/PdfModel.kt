package no.nav.syfo.client.pdfgen

import java.time.LocalDateTime
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.ValidationResult

data class PdfModel(
    val legeerklaering: Legeerklaering,
    val validationResult: ValidationResult,
    val mottattDato: LocalDateTime,
)
