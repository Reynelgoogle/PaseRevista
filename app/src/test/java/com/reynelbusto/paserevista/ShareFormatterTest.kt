package com.reynelbusto.paserevista

import com.reynelbusto.paserevista.domain.usecase.CardShareData
import com.reynelbusto.paserevista.domain.usecase.ShareFormatter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFormatterTest {

    private fun sample(ready: Boolean = false) = CardShareData(
        bed = "3",
        ready = ready,
        diagnosis = "Litiasis renal",
        scheduledProcedure = "Nefrolitotomía",
        openPendings = listOf("Urocultivo", "Valoración anestésica"),
        bloodGroup = "O+",
        currentState = "Estable",
        antibiotic = "Ciprofloxacino",
        patientName = null,
        hcNumber = null,
        address = null,
        customFields = listOf("Alergias" to "Penicilina"),
        outOfService = false,
        hasInterconsult = true,
    )

    @Test
    fun `simple incluye cama etiqueta y 6 campos`() {
        val text = ShareFormatter.cardSimple(sample())
        assertTrue(text.contains("CAMA 3"))
        assertTrue(text.contains("[PENDIENTE]"))
        assertTrue(text.contains("Dx: Litiasis renal"))
        assertTrue(text.contains("Proceder: Nefrolitotomía"))
        assertTrue(text.contains("Urocultivo"))
        assertTrue(text.contains("Grupo: O+"))
        assertTrue(text.contains("ATB: Ciprofloxacino"))
        assertTrue(text.contains("Interconsulta al servicio"))
        // La versión simple NO lleva datos del paciente ni apartados.
        assertFalse(text.contains("Alergias"))
    }

    @Test
    fun `completa suma datos del paciente y apartados`() {
        val data = sample(ready = true).copy(patientName = "Juan Pérez", hcNumber = "12345")
        val text = ShareFormatter.cardComplete(data)
        assertTrue(text.contains("[LISTO]"))
        assertTrue(text.contains("Juan Pérez"))
        assertTrue(text.contains("HC: 12345"))
        assertTrue(text.contains("Alergias: Penicilina"))
    }

    @Test
    fun `listado junta tarjetas con fecha`() {
        val text = ShareFormatter.cardList(listOf(sample(), sample().copy(bed = "5")), "Lunes 6 de octubre")
        assertTrue(text.startsWith("ENTREGA DE GUARDIA"))
        assertTrue(text.contains("CAMA 3"))
        assertTrue(text.contains("CAMA 5"))
    }

    @Test
    fun `campos vacios no aparecen`() {
        val data = sample().copy(diagnosis = null, antibiotic = null, openPendings = emptyList())
        val text = ShareFormatter.cardSimple(data)
        assertFalse(text.contains("Dx:"))
        assertFalse(text.contains("ATB:"))
        assertFalse(text.contains("Pendientes:"))
    }
}
