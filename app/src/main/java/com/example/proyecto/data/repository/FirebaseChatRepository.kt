package com.example.proyecto.data.repository

import com.example.proyecto.data.model.Amigo
import com.example.proyecto.data.model.Chat
import com.example.proyecto.data.model.Mensaje
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirebaseChatRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ChatRepository {

    private val currentUserUid: String
        get() = auth.currentUser?.uid ?: "u1"

    private val currentUserName: String
        get() = auth.currentUser?.displayName?.ifEmpty { "Yo" } ?: "Yo"

    private val _amigos = MutableStateFlow<List<Amigo>>(emptyList())
    override val amigos: StateFlow<List<Amigo>> = _amigos.asStateFlow()

    private val _todosLosPerfiles = MutableStateFlow<List<Amigo>>(emptyList())
    override val todosLosPerfiles: StateFlow<List<Amigo>> = _todosLosPerfiles.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    override val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    init {
        escucharChatsEnTiempoReal()
        escucharUsuariosFirestore()
        escucharAmigosEnTiempoReal()
    }

    private fun escucharUsuariosFirestore() {
        try {
            firestore.collection("users")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val perfilesDB = snapshot.documents.mapNotNull { doc ->
                        val uid = doc.getString("uid") ?: doc.id
                        if (uid == currentUserUid) return@mapNotNull null
                        val name = doc.getString("name") ?: doc.getString("nombre") ?: return@mapNotNull null
                        val email = doc.getString("email") ?: doc.getString("correo") ?: ""
                        val photoUrl = doc.getString("photoUrl")
                        Amigo(uid, name, email, photoUrl)
                    }
                    _todosLosPerfiles.value = perfilesDB
                }
        } catch (_: Exception) {
        }
    }

    private fun escucharAmigosEnTiempoReal() {
        try {
            val uid = currentUserUid
            firestore.collection("users").document(uid).collection("amigos")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val amigosDB = snapshot.documents.mapNotNull { doc ->
                        val amigoUid = doc.id
                        val name = doc.getString("nombre") ?: "Amigo"
                        val email = doc.getString("correo") ?: ""
                        val photoUrl = doc.getString("fotoUrl")
                        Amigo(amigoUid, name, email, photoUrl)
                    }
                    _amigos.value = amigosDB
                }
        } catch (_: Exception) {
        }
    }

    private fun escucharChatsEnTiempoReal() {
        try {
            firestore.collection("chats")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val chatsActualizados = snapshot.documents.mapNotNull { doc ->
                        val id = doc.id
                        val amigoId = doc.getString("amigoId") ?: id
                        val amigoNombre = doc.getString("amigoNombre") ?: "Usuario"
                        val fotoUrl = doc.getString("fotoUrl")
                        val ultimoMensaje = doc.getString("ultimoMensaje") ?: ""
                        val fecha = doc.getString("fecha") ?: "Ahora"

                        val chatExistente = _chats.value.find { it.id == id }
                        val chat = Chat(
                            id = id,
                            amigo = Amigo(amigoId, amigoNombre, "", fotoUrl),
                            ultimoMensaje = ultimoMensaje,
                            fechaUltimoMensaje = fecha,
                            mensajes = chatExistente?.mensajes ?: emptyList()
                        )
                        escucharMensajesDeChat(id)
                        chat
                    }
                    _chats.value = chatsActualizados
                }
        } catch (_: Exception) {
        }
    }

    override fun agregarAmigo(amigo: Amigo) {
        if (_amigos.value.none { it.id == amigo.id }) {
            _amigos.value = _amigos.value + amigo
        }
        try {
            val uid = currentUserUid
            val amigoData = mapOf(
                "id" to amigo.id,
                "nombre" to amigo.nombre,
                "correo" to amigo.correo,
                "fotoUrl" to amigo.fotoUrl,
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("users").document(uid).collection("amigos").document(amigo.id)
                .set(amigoData, SetOptions.merge())
        } catch (_: Exception) {
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

        val myUid = currentUserUid
        val chatId = if (myUid < amigo.id) "chat_${myUid}_${amigo.id}" else "chat_${amigo.id}_${myUid}"

        val nuevoChat = Chat(
            id = chatId,
            amigo = amigo,
            ultimoMensaje = "Conversación iniciada",
            fechaUltimoMensaje = "Ahora",
            mensajes = emptyList()
        )
        _chats.value = _chats.value + nuevoChat
        agregarAmigo(amigo)

        escucharMensajesDeChat(chatId)

        try {
            val chatData = mapOf(
                "id" to chatId,
                "participants" to listOf(myUid, amigo.id),
                "amigoId" to amigo.id,
                "amigoNombre" to amigo.nombre,
                "fotoUrl" to amigo.fotoUrl,
                "ultimoMensaje" to "Conversación iniciada",
                "fecha" to "Ahora",
                "lastUpdated" to System.currentTimeMillis()
            )
            firestore.collection("chats").document(chatId).set(chatData, SetOptions.merge())
        } catch (_: Exception) {
        }

        return nuevoChat
    }

    private fun escucharMensajesDeChat(chatId: String) {
        try {
            firestore.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val mensajes = snapshot.documents.mapNotNull { doc ->
                        val emisorId = doc.getString("emisorId") ?: ""
                        val emisorNombre = doc.getString("emisorNombre") ?: ""
                        val texto = doc.getString("texto") ?: ""
                        val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        Mensaje(doc.id, emisorId, emisorNombre, texto, ts)
                    }
                    if (mensajes.isNotEmpty()) {
                        _chats.value = _chats.value.map { chat ->
                            if (chat.id == chatId) {
                                chat.copy(
                                    mensajes = mensajes,
                                    ultimoMensaje = mensajes.lastOrNull()?.texto ?: chat.ultimoMensaje
                                )
                            } else {
                                chat
                            }
                        }
                    }
                }
        } catch (_: Exception) {
        }
    }

    override fun enviarMensaje(chatId: String, texto: String, emisorId: String, emisorNombre: String) {
        if (texto.isBlank()) return
        val senderId = if (emisorId.isNotBlank() && emisorId != "u1") emisorId else currentUserUid
        val senderName = if (emisorNombre.isNotBlank() && emisorNombre != "Yo") emisorNombre else currentUserName

        val nuevoMensaje = Mensaje(
            id = "m_${System.currentTimeMillis()}",
            emisorId = senderId,
            emisorNombre = senderName,
            texto = texto.trim(),
            timestamp = System.currentTimeMillis()
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

        try {
            val msgData = mapOf(
                "emisorId" to senderId,
                "emisorNombre" to senderName,
                "texto" to texto.trim(),
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("chats").document(chatId)
                .collection("messages").document(nuevoMensaje.id).set(msgData, SetOptions.merge())

            firestore.collection("chats").document(chatId).set(
                mapOf(
                    "ultimoMensaje" to texto.trim(),
                    "fecha" to "Ahora",
                    "lastUpdated" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        } catch (_: Exception) {
        }
    }
}
