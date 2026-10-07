package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.widget.nextFreeBed
import com.reynelbusto.paserevista.widget.quickRowSubtitle
import com.reynelbusto.paserevista.widget.quickRowTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickWidgetLogicTest {

    @Test
    fun `nextFreeBed vacio devuelve 1`() {
        assertEquals("1", nextFreeBed(emptyList()))
    }

    @Test
    fun `nextFreeBed salta los ocupados`() {
        assertEquals("3", nextFreeBed(listOf("1", "2")))
        assertEquals("1", nextFreeBed(listOf("2", "3")))
        assertEquals("2", nextFreeBed(listOf("1", "3", "4")))
    }

    @Test
    fun `nextFreeBed ignora camas no numericas y no positivas`() {
        assertEquals("3", nextFreeBed(listOf("A", "1", " 2 ")))
        assertEquals("1", nextFreeBed(listOf("0", "-1", "X")))
    }

    @Test
    fun `quickRowTitle con y sin diagnostico`() {
        assertEquals("CAMA 1 · HBP", quickRowTitle("1", "HBP"))
        assertEquals("CAMA 2", quickRowTitle("2", null))
        assertEquals("CAMA 3", quickRowTitle("3", "   "))
        assertEquals("CAMA 4 · RTU próstata", quickRowTitle(" 4 ", "  RTU próstata  "))
    }

    @Test
    fun `quickRowSubtitle singular y plural`() {
        assertEquals("Sin pendientes", quickRowSubtitle(0))
        assertEquals("Sin pendientes", quickRowSubtitle(-2))
        assertEquals("1 pendiente", quickRowSubtitle(1))
        assertEquals("3 pendientes", quickRowSubtitle(3))
    }
}
