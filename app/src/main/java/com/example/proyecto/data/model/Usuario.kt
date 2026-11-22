package com.example.proyecto.data.model

const val DEFAULT_PROFILE_PICTURE = "https://cdn-icons-png.flaticon.com/512/149/149071.png"

data class Usuario(
    val id: String = "u1",
    val nombre: String = "",
    val edad: Int = 0,
    val fechaNacimiento: String = "",
    val estrato: Int = 1,
    val direccion: String = "",
    val correo: String = "",
    val telefono: String = "",
    val fotoUrl: String? = DEFAULT_PROFILE_PICTURE
) {
    val effectiveFotoUrl: String
        get() = if (!fotoUrl.isNullOrBlank()) fotoUrl else DEFAULT_PROFILE_PICTURE
}
