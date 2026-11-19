package com.example.services.bio

import java.math.BigDecimal
import java.math.RoundingMode

// Extensão genérica e reutilizável em qualquer lugar do projeto (Evita duplicação)
fun Float.roundToOneDecimal(): Float {
    return BigDecimal(this.toDouble()).setScale(1, RoundingMode.HALF_UP).toFloat()
}

data class BioimpedanceData(
    val peso: Float?,
    val imc: Float?,
    val percentualGordura: Float?,
    val taxaMuscular: Float?,
    val massaMagra: Float?,
    val gorduraSubcutanea: Float?,
    val gorduraVisceral: Float?,
    val aguaCorporal: Float?,
    val massaMuscularEsqueletica: Float?,
    val massaMuscular: Float?,
    val massaOssea: Float?,
    val proteina: Float?,
    val tmb: Int?,
    val idadeMetabolica: Int?,
    val isSimulated: Boolean = false
)

sealed interface BioimpedanceState {
    object Idle : BioimpedanceState
    object Loading : BioimpedanceState
    data class Success(val data: BioimpedanceData) : BioimpedanceState
    data class Failure(val errorMessage: String) : BioimpedanceState
    object RetryAvailable : BioimpedanceState
    object ManualEntry : BioimpedanceState
}
