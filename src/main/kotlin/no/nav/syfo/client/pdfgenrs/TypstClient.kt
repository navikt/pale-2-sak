package no.nav.syfo.client.pdfgenrs

import java.time.LocalDateTime
import no.nav.syfo.logger
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper

class TypstClient(
    private val typstBinaryPath: String = "/app/typst-pdf/typst",
    private val templatePath: String = "/app/typst-pdf/pale-2.typ",
    private val fontPath: String = "/app/typst-pdf/fonts",
) {
    fun createPdf(payload: PdfrsModel): ByteArray {
        val jsonData = objectMapper.writeValueAsString(payload)

        val process =
            ProcessBuilder(
                    typstBinaryPath,
                    "compile",
                    "--pdf-standard=a-2a",
                    "--font-path=$fontPath",
                    "--input=data=$jsonData",
                    templatePath,
                    "-",
                )
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

        var stderr = ""
        val stderrThread = Thread { stderr = process.errorStream.bufferedReader().readText() }
        stderrThread.start()
        val pdfBytes = process.inputStream.readBytes()
        stderrThread.join()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error("Typst compilation failed with exit code $exitCode: $stderr")
            throw RuntimeException("Typst compilation failed: $stderr")
        }

        return pdfBytes
    }
}

fun createTypstPayload(
    legeerklaring: Legeerklaering,
    validationResult: ValidationResult,
    mottattDato: LocalDateTime,
): PdfrsModel =
    PdfrsModel(
        legeerklaering = mapToLegeerklaringWithoutIllegalCharacters(legeerklaring),
        validationResult = validationResult,
        mottattDato = mottattDato,
    )

fun mapToLegeerklaringWithoutIllegalCharacters(legeerklaring: Legeerklaering): Legeerklaering {
    val legeerklaringAsString = objectMapper.writeValueAsString(legeerklaring)
    val legeerklaringAsStringWithoutIllegalCharacters =
        legeerklaringAsString.replace(regex = Regex("[^\\x00-\\x7FæøåÆØÅ]"), "")
    return objectMapper.readValue(
        legeerklaringAsStringWithoutIllegalCharacters,
        Legeerklaering::class.java,
    )
}
