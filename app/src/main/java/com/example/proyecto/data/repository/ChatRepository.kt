package com.example.proyecto.data.repository

import com.example.proyecto.data.model.Amigo
import com.example.proyecto.data.model.Chat
import com.example.proyecto.data.model.Mensaje
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ChatRepository {
    fun iniciarSesion(uid: String, nombre: String, fotoUrl: String? = null) {}
    val amigos: StateFlow<List<Amigo>>
    val chats: StateFlow<List<Chat>>
    val todosLosPerfiles: StateFlow<List<Amigo>>

    fun agregarAmigo(amigo: Amigo)
    fun esAmigo(usuarioId: String): Boolean
    fun obtenerOCrearChatCon(amigo: Amigo): Chat
    fun enviarMensaje(chatId: String, texto: String, emisorId: String, emisorNombre: String)
}

class MockChatRepository : ChatRepository {

    private val muestraTodos = listOf(
        Amigo("u2", "Carlos Mendoza", "carlos.m@correo.com", "https://i.pravatar.cc/150?u=u2"),
        Amigo("u3", "Laura Gómez", "laura.g@correo.com", "https://i.pravatar.cc/150?u=u3"),
        Amigo("u4", "Felipe Hernández", "felipe.h@correo.com", "https://i.pravatar.cc/150?u=u4"),
        Amigo("u5", "Andrea Pérez", "andrea.p@correo.com", "https://i.pravatar.cc/150?u=u5"),
        Amigo("u6", "Daniela K.", "daniela.k@correo.com", "https://i.pravatar.cc/150?u=u6"),
        Amigo("e1", "Tech Testing Corp", "empresa@tech.com", "https://i.pravatar.cc/150?u=e1")
    )

    private val muestraAmigos = listOf(
        muestraTodos[0],
        muestraTodos[1],
        muestraTodos[2]
    )

    private val muestraChats = listOf(
        Chat(
            id = "chat_u2",
            amigo = muestraAmigos[0],
            ultimoMensaje = "¡Hola! ¿Cómo te fue en la prueba del auricular?",
            fechaUltimoMensaje = "10:45 AM",
            mensajes = listOf(
                Mensaje(id = "m1", emisorId = "u2", emisorNombre = "Carlos Mendoza", texto = "¡Hola! ¿Cómo te fue en la prueba del auricular?"),
                Mensaje(id = "m2", emisorId = "u1", emisorNombre = "Yo", texto = "¡Excelente! El aislamiento de ruido es fantástico.")
            )
        ),
        Chat(
            id = "chat_u3",
            amigo = muestraAmigos[1],
            ultimoMensaje = "Te recomiendo el test del snack orgánico.",
            fechaUltimoMensaje = "Ayer",
            mensajes = listOf(
                Mensaje(id = "m3", emisorId = "u3", emisorNombre = "Laura Gómez", texto = "Te recomiendo el test del snack orgánico.")
            )
        )
    )

    private val _amigos = MutableStateFlow(muestraAmigos)
    override val amigos: StateFlow<List<Amigo>> = _amigos.asStateFlow()

    private val _chats = MutableStateFlow(muestraChats)
    override val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _todosLosPerfiles = MutableStateFlow(muestraTodos)
    override val todosLosPerfiles: StateFlow<List<Amigo>> = _todosLosPerfiles.asStateFlow()

    override fun agregarAmigo(amigo: Amigo) {
        if (_amigos.value.none { it.id == amigo.id }) {
            _amigos.value = _amigos.value + amigo
        }
    }

    override fun esAmigo(usuarioId: String): Boolean {
        return _amigos.value.any { it.id == usuarioId }
    }

    override fun obtenerOCrearChatCon(amigo: Amigo): Chat {
        val chatExistente = _chats.value.find { it.amigo.id == amigo.id }
        if (chatExistente != null) {
            return chatExistente
        }
        val nuevoChat = Chat(
            id = "chat_${amigo.id}",
            amigo = amigo,
            ultimoMensaje = "Chat iniciado",
            fechaUltimoMensaje = "Ahora",
            mensajes = emptyList()
        )
        _chats.value = _chats.value + nuevoChat
        agregarAmigo(amigo)
        return nuevoChat
    }

    override fun enviarMensaje(chatId: String, texto: String, emisorId: String, emisorNombre: String) {
        if (texto.isBlank()) return
        val nuevoMensaje = Mensaje(
            emisorId = emisorId,
            emisorNombre = emisorNombre,
            texto = texto.trim()
        )
        _chats.value = _chats.value.map { chat ->
            if (chat.id == chatId) {
                chat.copy(
                    ultimoMensaje = texto.trim(),
                    fechaUltimoMensaje = "Ahora",
                    mensajes = chat.mensajes + nuevoMensaje
                )
            } else {
                chat
            }
        }
    }
}
