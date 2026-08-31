package no.nav.syfo.client.pdfgenrs

import java.awt.Font
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import no.nav.syfo.jsonMapper
import no.nav.syfo.logger
import no.nav.syfo.model.Legeerklaering
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.secureLogger

class TypstClient(
    private val typstBinaryPath: String = "/app/typst-pdf/typst",
    private val templatePath: String = "/app/typst-pdf/pale-2.typ",
    private val fontPath: String = "/app/typst-pdf/fonts",
) {
    private val fonts: List<Font> by lazy {
        File(fontPath)
            .listFiles { _, name -> name.endsWith(".ttf", ignoreCase = true) }
            ?.mapNotNull { file ->
                runCatching { Font.createFont(Font.TRUETYPE_FONT, file) }
                    .onFailure { logger.warn("Could not load font ${file.name}: ${it.message}") }
                    .getOrNull()
            } ?: emptyList()
    }

    fun createPdf(payload: PdfrsModel): ByteArray {
        val jsonData = jsonMapper.writeValueAsString(payload)

        return try {
            runTypst(payload.legeerklaering.id, jsonData)
        } catch (e: TypstCompilationException) {
            val dropped = mutableListOf<String>()
            val filtered = filterUndisplayable(jsonData, dropped)
            logger.warn("Error during typst, retrying by removing invalid codepoints")
            secureLogger.warn(
                "Typst failed for legeerklæring id ${payload.legeerklaering.id}; " +
                    "retrying after dropping undisplayable chars: $dropped. " +
                    "Original error: ${e.message}"
            )
            runTypst(payload.legeerklaering.id, filtered)
        }
    }

    private fun canDisplay(codePoint: Int): Boolean = fonts.any { it.canDisplay(codePoint) }

    private fun isFormatChar(codePoint: Int): Boolean =
        codePoint.toChar().category == CharCategory.FORMAT

    private fun filterUndisplayable(input: String, dropped: MutableList<String>): String =
        input
            .codePoints()
            .filter { cp ->
                val ok = !isFormatChar(cp) && (cp < 0x80 || canDisplay(cp))
                if (!ok) dropped.add("U+%04X".format(cp))
                ok
            }
            .collect(::StringBuilder, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString()

    private fun runTypst(id: String, jsonData: String): ByteArray {
        val dataFile = Files.createTempFile(id, ".json")
        try {
            Files.writeString(dataFile, jsonData)

            val process =
                ProcessBuilder(
                        typstBinaryPath,
                        "compile",
                        "--pdf-standard=a-2a",
                        "--pdf-standard=ua-1",
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
                logger.error("Typst compilation failed with exit code $exitCode")
                secureLogger.error("Typst compilation failed with exit code $exitCode: $stderr")
                throw TypstCompilationException("Typst compilation failed: $stderr")
            }

            return pdfBytes
        } finally {
            Files.deleteIfExists(dataFile)
        }
    }
}

class TypstCompilationException(message: String) : RuntimeException(message)

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
