package no.nav.syfo.model.kafka

import no.nav.syfo.model.ValidationResult

data class LegeerklaeringKafkaMessage(
    val legeerklaeringObjectId: String,
    val validationResult: ValidationResult,
    val vedlegg: List<String>?
)
