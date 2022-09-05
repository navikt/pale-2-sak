package no.nav.syfo.model

import java.time.LocalDateTime

data class PdfModel(
    val legeerklaering: Legeerklaering,
    val validationResult: ValidationResult,
    val mottattDato: LocalDateTime,
)
