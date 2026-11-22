package com.example.proyecto.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object CloudinaryUploader {
    var cloudName = "jchybuxk"
    var uploadPreset = "aaaaaa"

    private val client = OkHttpClient()

    suspend fun uploadImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir la imagen seleccionada")
        val imageBytes = inputStream.use { it.readBytes() }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart(
                "file",
                "profile.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(responseText)
                json.getString("secure_url")
            } else {
                val errorMessage = try {
                    JSONObject(responseText).getJSONObject("error").getString("message")
                } catch (_: Exception) {
                    responseText
                }
                throw Exception("Error Cloudinary ($errorMessage)")
            }
        }
    }
}
