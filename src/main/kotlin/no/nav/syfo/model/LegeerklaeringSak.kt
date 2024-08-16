package no.nav.syfo.model

data class LegeerklaeringSak(
    val receivedLegeerklaering: ReceivedLegeerklaering,
    val validationResult: ValidationResult,
    val vedlegg: List<String>?
)
