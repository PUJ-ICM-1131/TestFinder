package com.example.proyecto.data.model

data class Amigo(
    val id: String,
    val nombre: String,
    val correo: String = "",
    val fotoUrl: String? = DEFAULT_PROFILE_PICTURE
) {
    val effectiveFotoUrl: String
        get() = if (!fotoUrl.isNullOrBlank()) fotoUrl else DEFAULT_PROFILE_PICTURE
}

data class Mensaje(
    val id: String = System.currentTimeMillis().toString(),
    val emisorId: String,
    val emisorNombre: String,
    val texto: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class Chat(
    val id: String,
    val amigo: Amigo,
    val ultimoMensaje: String = "",
    val fechaUltimoMensaje: String = "Ahora",
    val mensajes: List<Mensaje> = emptyList()
)
