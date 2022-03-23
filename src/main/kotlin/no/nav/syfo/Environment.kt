package no.nav.syfo

import no.nav.syfo.util.getFileAsString

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "pale-2-sak"),
    val dokArkivUrl: String = getEnvVar("DOK_ARKIV_URL"),
    val securityTokenServiceURL: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val pdfgen: String = getEnvVar("PDF_GEN_URL", "http://syfopdfgen.teamsykmelding/api/v1/genpdf/pale-2/pale-2"),
    val paleVedleggBucketName: String = getEnvVar("PALE_VEDLEGG_BUCKET_NAME"),
    val legeerklaeringBucketName: String = getEnvVar("PALE_BUCKET_NAME"),
    val legeerklaringTopic: String = "teamsykmelding.legeerklaering"
)

data class VaultSecrets(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
