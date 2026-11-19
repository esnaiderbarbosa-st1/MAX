package com.example.data

import com.example.data.model.RunningSession
import com.example.data.model.SplitSelectorMode
import com.example.data.model.UserProfileData
import com.example.data.model.buildRunPhysiologyDossier
import com.example.data.model.buildSplitsForMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPhysiologyDossierTest {

    @Test
    fun buildRunPhysiologyDossier_withoutGpsAndWithoutHr_returnsEmptyTelemetryAndZeroSyntheticMetrics() {
        val session = RunningSession(
            id = "test_no_gps_no_hr",
            date = 1_730_000_000_000L,
            durationSec = 1800,
            distanceMeters = 5000,
            avgPaceSecPerKm = 360,
            avgHr = null,
            maxHr = null,
            hrTimeline = null,
            routePointsJson = null,
            kmSplitsJson = null
        )

        val dossier = buildRunPhysiologyDossier(
            session = session,
            userProfile = null,
            gpsPoints = emptyList()
        )

        assertTrue("telemetrySamples deve ser vazia sem GPS e sem FC", dossier.telemetrySamples.isEmpty())
        assertEquals(0, dossier.avgCadenceSpm)
        assertEquals(0, dossier.maxCadenceSpm)
        assertEquals(0, dossier.avgStrideLengthCm)
        assertEquals(0, dossier.maxStrideLengthCm)
        assertEquals(0, dossier.totalSteps)
        assertEquals(0, dossier.avgHeartRateBpm)
        assertEquals(0, dossier.maxHeartRateBpm)
        assertEquals(0, dossier.effectiveMaxHeartRateBpm)
        assertTrue("hrZones5 deve ser vazia sem leituras reais de FC", dossier.hrZones5.isEmpty())
        assertTrue("kmSplits deve ser vazia sem GPS e sem parciais gravadas", dossier.kmSplits.isEmpty())
        assertEquals(0, dossier.vitalityScoreGain)
        assertEquals("Atleta", dossier.athleteName)
        assertEquals("Sem dados de FC para classificar o efeito do treino", dossier.trainingEffectTitle)
        assertEquals("Consistência indisponível — dados de ritmo insuficientes", dossier.consistencyVerdictTitle)

        // Confirma também que buildSplitsForMode não fabrica parciais com < 6 pontos GPS
        assertTrue(buildSplitsForMode(session, SplitSelectorMode.BY_BLOCK, emptyList()).isEmpty())
        assertTrue(buildSplitsForMode(session, SplitSelectorMode.ONE_KM_MAIN, emptyList()).isEmpty())
    }

    @Test
    fun buildRunPhysiologyDossier_withKnownHrTimeline_preservesExactReadingsWithoutNoise() {
        val expectedHr = listOf(132, 140, 148, 155, 161, 158, 150)
        val session = RunningSession(
            id = "test_known_hr",
            date = 1_730_000_000_000L,
            durationSec = 1200,
            distanceMeters = 3000,
            avgPaceSecPerKm = 400,
            avgHr = 149,
            maxHr = 161,
            hrTimeline = expectedHr.joinToString(","),
            routePointsJson = null,
            kmSplitsJson = null
        )
        val profileWithoutAge = UserProfileData(
            nome = "Maria Silva",
            idade = 0,
            peso = 62f
        )

        val dossier = buildRunPhysiologyDossier(
            session = session,
            userProfile = profileWithoutAge,
            gpsPoints = emptyList()
        )

        assertEquals(expectedHr, dossier.telemetrySamples.map { it.heartRateBpm })
        assertTrue("Sem GPS, pace por amostra deve permanecer 0", dossier.telemetrySamples.all { it.paceSecPerKm == 0f })
        assertEquals(0, dossier.avgCadenceSpm)
        assertEquals("Maria Silva", dossier.athleteName)
        // Sem idade no perfil, não assume 30 anos: calibra com FC máxima medida da sessão (161 bpm)
        assertEquals(161, dossier.effectiveMaxHeartRateBpm)
        assertEquals(5, dossier.hrZones5.size)
        assertTrue(dossier.hrZones5.any { it.percentage > 0f })
    }

    @Test
    fun buildRunPhysiologyDossier_slowWalkingPace_allowsUpTo3600SecPerKm() {
        val session = RunningSession(
            id = "test_slow_walk",
            date = 1_730_000_000_000L,
            durationSec = 1000,
            distanceMeters = 500,
            avgPaceSecPerKm = 2000,
            avgHr = null,
            maxHr = null,
            hrTimeline = null,
            routePointsJson = null,
            kmSplitsJson = null
        )

        val dossier = buildRunPhysiologyDossier(
            session = session,
            userProfile = null,
            gpsPoints = emptyList()
        )

        assertEquals(2000, dossier.avgPaceSecPerKm)
        assertEquals(2000, dossier.maxPaceSecPerKm)
    }
}
