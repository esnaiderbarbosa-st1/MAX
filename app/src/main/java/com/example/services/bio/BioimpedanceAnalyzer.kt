package com.example.services.bio

import com.example.BuildConfig
import com.example.data.model.TelemetryEventType
import com.example.services.telemetry.Telemetry
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// --- ENVELOPES DE INFRAESTRUTURA DA API DO GEMINI ---
data class GeminiResponse(val candidates: List<GeminiCandidate>)
data class GeminiCandidate(val content: GeminiContent)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiPart(val text: String)

interface BioimpedanceAnalyzer {
    suspend fun analyzeImage(imageBytes: ByteArray): Result<BioimpedanceData>
}

class GeminiAnalyzer(
    private val apiKey: String,
    private val modelId: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrlOverride: String? = null
) : BioimpedanceAnalyzer {
    
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    override suspend fun analyzeImage(imageBytes: ByteArray): Result<BioimpedanceData> = withContext(Dispatchers.IO) {
        val fingerprint = try {
            val digest = MessageDigest.getInstance("SHA-256").digest(apiKey.toByteArray())
            digest.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            "HASH_ERROR"
        }

        Telemetry.log(
            TelemetryEventType.CUSTOM, mapOf(
                "action" to "BIO_ANALYSIS_STARTED",
                "build_variant" to if (BuildConfig.DEBUG) "debug" else "release",
                "gemini_model_id" to modelId,
                "api_key_fingerprint" to fingerprint,
                "execution_path" to "REAL"
            )
        )

        val finalUrl = if (!baseUrlOverride.isNullOrBlank()) {
            "${baseUrlOverride}${modelId}:generateContent?key=${apiKey}"
        } else {
            "https://generativelanguage.googleapis.com/v1beta/models/${modelId}:generateContent?key=${apiKey}"
        }

        val base64Data = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val prompt = "Analyze this bioimpedance report image. Extract data into raw JSON matching keys: peso, imc, percentualGordura, taxaMuscular, massaMagra, gorduraSubcutanea, gorduraVisceral, aguaCorporal, massaMuscularEsqueletica, massaMuscular, massaOssea, proteina, tmb, idadeMetabolica."

        val jsonPayload = """
            {
                "contents": [{
                    "parts": [
                        { "text": "$prompt" },
                        { "inlineData": { "mimeType": "image/jpeg", "data": "$base64Data" } }
                    ]
                }],
                "generationConfig": {
                    "responseMimeType": "application/json"
                }
            }
        """.trimIndent()

        val request = Request.Builder().url(finalUrl).post(jsonPayload.toRequestBody(mediaType)).build()

        try {
            client.newCall(request).execute().use { response ->
                Telemetry.log(
                    TelemetryEventType.CUSTOM, mapOf(
                        "action" to "BIO_ANALYSIS_RESPONSE",
                        "http_status_result" to response.code
                    )
                )

                if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP Error ${response.code}"))

                val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val responseEnvelope = moshi.adapter(GeminiResponse::class.java).fromJson(bodyString)
                val rawCleanJson = responseEnvelope?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

                val data = moshi.adapter(BioimpedanceData::class.java).fromJson(rawCleanJson)
                if (data != null) Result.success(data) else Result.failure(Exception("Parsing error"))
            }
        } catch (e: Exception) {
            Telemetry.log(TelemetryEventType.ERROR, mapOf("message" to (e.message ?: "Network Exception")))
            Result.failure(e)
        }
    }
}

private class MockAnalyzer(private val modelId: String) : BioimpedanceAnalyzer {
    override suspend fun analyzeImage(imageBytes: ByteArray): Result<BioimpedanceData> = withContext(Dispatchers.IO) {
        Telemetry.log(
            TelemetryEventType.CUSTOM, mapOf(
                "action" to "BIO_ANALYSIS_STARTED",
                "build_variant" to "debug",
                "gemini_model_id" to modelId,
                "api_key_fingerprint" to "MOCK_RUN_NO_KEY",
                "execution_path" to "FALLBACK"
            )
        )
        Result.success(
            BioimpedanceData(
                peso = 80.0f, imc = 24.0f, percentualGordura = 15.0f, taxaMuscular = 40.0f,
                massaMagra = 68.0f, gorduraSubcutanea = 12.0f, gorduraVisceral = 5.0f,
                aguaCorporal = 60.0f, massaMuscularEsqueletica = 35.0f, massaMuscular = 55.0f,
                massaOssea = 3.0f, proteina = 19.0f, tmb = 1800, idadeMetabolica = 25,
                isSimulated = true
            )
        )
    }
}

object BioimpedanceAnalyzerFactory {
    fun create(apiKey: String, modelId: String, client: OkHttpClient = OkHttpClient()): BioimpedanceAnalyzer {
        return if (BuildConfig.DEBUG) {
            if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) MockAnalyzer(modelId) else GeminiAnalyzer(apiKey, modelId, client)
        } else {
            if (apiKey == "MY_GEMINI_API_KEY" || apiKey.isBlank()) {
                throw IllegalStateException("RELEASE build cannot be initialized with placeholder API Keys.")
            }
            GeminiAnalyzer(apiKey, modelId, client)
        }
    }
}
