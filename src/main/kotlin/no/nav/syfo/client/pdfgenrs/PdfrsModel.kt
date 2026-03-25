package no.nav.syfo.client.pdfgenrs

import java.time.LocalDateTime
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.ValidationResult

data class PdfrsModel(
    val legeerklaering: Legeerklaering,
    val validationResult: ValidationResult,
    val mottattDato: LocalDateTime,
)
