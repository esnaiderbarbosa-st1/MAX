package com.example.domain.recomp

import com.example.data.model.IndicatorStatus
import com.example.data.model.UserProfileData
import kotlin.math.roundToInt

/**
 * Lógica centralizada de referências fisiológicas de bioimpedância por perfil.
 */
object BioimpedanceReferences {

    const val BMI_NORMAL_MIN = 18.5f
    const val BMI_NORMAL_MAX = 24.9f
    const val BMI_OVERWEIGHT_MAX = 29.9f

    /**
     * Rótulo curto da faixa de IMC segundo a OMS (18,5–24,9 normal; até 29,9 sobrepeso),
     * válido para todos os perfis adultos.
     */
    fun bmiBandLabel(bmi: Float): String = when {
        bmi < BMI_NORMAL_MIN -> "Abaixo do peso"
        bmi <= BMI_NORMAL_MAX -> "Normal"
        bmi <= BMI_OVERWEIGHT_MAX -> "Sobrepeso"
        else -> "Obesidade"
    }

    /**
     * Classificação de status do IMC segundo as faixas da OMS (18,5–24,9 normal; até 29,9 sobrepeso),
     * válida para todos os perfis.
     */
    fun classifyBmi(bmi: Float): IndicatorStatus = when {
        bmi < BMI_NORMAL_MIN -> IndicatorStatus.BAIXO
        bmi <= BMI_NORMAL_MAX -> IndicatorStatus.MEDIO
        bmi <= BMI_OVERWEIGHT_MAX -> IndicatorStatus.SOBREPESO
        else -> IndicatorStatus.ALTO
    }

    /**
     * Faixa de peso saudável (kg) correspondente ao IMC 18,5–24,9 a partir da altura válida (100..230 cm).
     * Retorna null se a altura for ausente ou inválida.
     */
    fun healthyWeightRangeKg(heightCm: Int?): ClosedFloatingPointRange<Float>? {
        val validHeight = heightCm?.takeIf { it in 100..230 } ?: return null
        val heightM = validHeight / 100f
        val h2 = heightM * heightM
        val minKg = round1(BMI_NORMAL_MIN * h2)
        val maxKg = round1(BMI_NORMAL_MAX * h2)
        return minKg..maxKg
    }

    /**
     * TEMPORÁRIO: retorna true apenas para o perfil cujo nome contém "Esnaider" (ignorando maiúsculas),
     * até que metas fisiológicas personalizáveis por perfil existam no banco de dados.
     */
    fun hasPersonalGoals(profile: UserProfileData?): Boolean {
        val name = profile?.nome ?: return false
        return name.contains("Esnaider", ignoreCase = true)
    }

    private fun round1(value: Float): Float = ((value * 10f).roundToInt() / 10f)
}
