package com.example.services.supabase

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

object SupabaseService {

    const val SUPABASE_URL = "https://icnxqseqrustnexmbgku.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_nkr4nr6K_02mhx3ytgl0rw_Uet8H_"
    const val SUPABASE_FUNCTION_URL = "https://icnxqseqrustnexmbgku.supabase.co/functions/v1/analyze-bioimpedance"

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    data class SupabaseRequest(val imageBase64: String)

    data class SupabaseResponse(
        val reading_date: String? = null,
        val weight_kg: Float? = null,
        val body_fat_pct: Float? = null,
        val muscle_rate_pct: Float? = null,
        val imc: Float? = null,
        val fat_free_mass_kg: Float? = null,
        val subcutaneous_fat_pct: Float? = null,
        val visceral_fat: Float? = null,
        val body_water_pct: Float? = null,
        val skeletal_muscle_mass_kg: Float? = null,
        val muscle_mass_kg: Float? = null,
        val bone_mass_kg: Float? = null,
        val protein_pct: Float? = null,
        val bmr_kcal: Int? = null,
        val metabolic_age: Int? = null,
        val _warnings: List<String>? = null,
        val _partialData: Boolean? = null
    )

    suspend fun analyzeBioimpedance(base64Image: String): SupabaseResponse {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val requestAdapter = moshi.adapter(SupabaseRequest::class.java)
                val jsonPayload = requestAdapter.toJson(SupabaseRequest(base64Image))

                val request = Request.Builder()
                    .url(SUPABASE_FUNCTION_URL)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .post(jsonPayload.toRequestBody(mediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorMsg = response.body?.string() ?: "Sem detalhes"
                        throw Exception("Erro na Edge Function (status ${response.code}): $errorMsg")
                    }

                    val bodyString = response.body?.string() ?: throw Exception("Resposta vazia da Edge Function")
                    val responseAdapter = moshi.adapter(SupabaseResponse::class.java)
                    responseAdapter.fromJson(bodyString) ?: throw Exception("Erro ao ler JSON retornado pela Edge Function")
                }
            } catch (e: UnknownHostException) {
                throw Exception("Sem conexão. Conecte-se à internet e tente novamente.")
            } catch (e: SocketTimeoutException) {
                throw Exception("A análise está demorando. Verifique sua conexão.")
            } catch (e: java.io.IOException) {
                if (e.message?.contains("timeout", ignoreCase = true) == true) {
                    throw Exception("A análise está demorando. Verifique sua conexão.")
                } else {
                    throw Exception("Sem conexão. Conecte-se à internet e tente novamente.")
                }
            } catch (e: Exception) {
                throw Exception(e.message ?: "Erro desconhecido na análise de bioimpedância")
            }
        }
    }
}
