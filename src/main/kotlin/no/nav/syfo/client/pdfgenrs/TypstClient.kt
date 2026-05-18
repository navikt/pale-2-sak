package no.nav.syfo.client.pdfgenrs

import java.nio.file.Files
import java.time.LocalDateTime
import no.nav.syfo.logger
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.secureLogger

class TypstClient(
    private val typstBinaryPath: String = "/app/typst-pdf/typst",
    private val templatePath: String = "/app/typst-pdf/pale-2.typ",
    private val fontPath: String = "/app/typst-pdf/fonts",
) {
    fun createPdf(payload: PdfrsModel): ByteArray {
        val illegals = mutableListOf<Char>()
        val jsonData =
            objectMapper.writeValueAsString(payload).filterNot {
                val illegal =
                    (it.category == CharCategory.PRIVATE_USE ||
                        it.category == CharCategory.OTHER_LETTER)
                if (illegal) {
                    illegals.add(it)
                }
                illegal
            }
        if (illegals.isNotEmpty()) {
            secureLogger.warn(
                "Illegal chars found in legeerklæring id: ${payload.legeerklaering.id}. chars: $illegals"
            )
        }
        val dataFile = Files.createTempFile(payload.legeerklaering.id, ".json")
        try {
            Files.writeString(dataFile, jsonData)

            val process =
                ProcessBuilder(
                        typstBinaryPath,
                        "compile",
                        "--pdf-standard=a-2a",
                        "--root=/",
                        "--font-path=$fontPath",
                        "--input=data-path=${dataFile}",
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
        } finally {
            Files.deleteIfExists(dataFile)
        }
    }
}

fun createTypstPayload(
    legeerklaring: Legeerklaering,
    validationResult: ValidationResult,
    mottattDato: LocalDateTime,
): PdfrsModel =
    PdfrsModel(
        legeerklaering = legeerklaring,
        validationResult = validationResult,
        mottattDato = mottattDato,
    )
