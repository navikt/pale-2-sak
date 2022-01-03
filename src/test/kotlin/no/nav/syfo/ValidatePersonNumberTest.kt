package no.nav.syfo

import no.nav.syfo.validation.validatePersonAndDNumber
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class ValidatePersonNumberTest {
    @Test
    internal fun `Should check validate as fnr`() {
        val generateFnr = generatePersonNumber(LocalDate.of(1991, 1, 1), false)
        val validFnr = validatePersonAndDNumber(generateFnr)
        validFnr shouldBeEqualTo true
    }

    @Test
    internal fun `Should check validate as d-number`() {
        val generateDnumber = generatePersonNumber(LocalDate.of(1991, 1, 1), true)
        val validdnumber = validatePersonAndDNumber(generateDnumber)
        validdnumber shouldBeEqualTo true
    }
}

fun generatePersonNumber(bornDate: LocalDate, useDNumber: Boolean = false): String {
    val personDate = bornDate.format(DateTimeFormatter.ofPattern("ddMMyy")).let {
        if (useDNumber) "${it[0] + 4}${it.substring(1)}" else it
    }
    return (if (bornDate.year >= 2000) (75011..99999) else (11111..50099))
        .map { "$personDate$it" }
        .first {
            validatePersonAndDNumber(it)
        }
}
