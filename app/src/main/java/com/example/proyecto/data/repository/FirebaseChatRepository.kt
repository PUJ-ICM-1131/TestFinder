package com.example.proyecto.data.repository

import android.util.Log
import com.example.proyecto.data.model.Amigo
import com.example.proyecto.data.model.Chat
import com.example.proyecto.data.model.Mensaje
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirebaseChatRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChatRepository {

    private companion object {
        const val TAG = "FirebaseChatRepo"
    }

    private var myUid: String = ""
    private var myName: String = "Yo"
    private var myPhoto: String? = null

    private val _amigos = MutableStateFlow<List<Amigo>>(emptyList())
    override val amigos: StateFlow<List<Amigo>> = _amigos.asStateFlow()

    private val _todosLosPerfiles = MutableStateFlow<List<Amigo>>(emptyList())
    override val todosLosPerfiles: StateFlow<List<Amigo>> = _todosLosPerfiles.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    override val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()

    override fun iniciarSesion(uid: String, nombre: String, fotoUrl: String?) {
        myName = nombre.ifBlank { "Yo" }
        myPhoto = fotoUrl
        if (uid == myUid) return

        detenerListeners()
        myUid = uid
        _chats.value = emptyList()
        _amigos.value = emptyList()
        _todosLosPerfiles.value = emptyList()

        if (uid.isBlank()) return
        escucharChats()
        escucharUsuarios()
        escucharAmigos()
    }

    private fun detenerListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
        messageListeners.values.forEach { it.remove() }
        messageListeners.clear()
    }

    private fun escucharUsuarios() {
        listeners += firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando users", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                _todosLosPerfiles.value = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: doc.id
                    if (uid == myUid) return@mapNotNull null
                    val name = doc.getString("name") ?: doc.getString("nombre")
                    ?: return@mapNotNull null
                    Amigo(
                        id = uid,
                        nombre = name,
                        correo = doc.getString("email") ?: doc.getString("correo") ?: "",
                        fotoUrl = doc.getString("photoUrl") ?: doc.getString("fotoUrl")
                    )
                }
            }
    }

    private fun escucharAmigos() {
        listeners += firestore.collection("users").document(myUid).collection("amigos")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando amigos", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                _amigos.value = snapshot.documents.map { doc ->
                    Amigo(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: "Amigo",
                        correo = doc.getString("correo") ?: "",
                        fotoUrl = doc.getString("fotoUrl")
                    )
                }
            }
    }

    private fun escucharChats() {
        listeners += firestore.collection("chats")
            .whereArrayContains("participants", myUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando chats", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val actuales = _chats.value.associateBy { it.id }

                val lista = snapshot.documents
                    .sortedByDescending { it.getLong("lastUpdated") ?: 0L }
                    .map { doc ->
                        val participants = (doc.get("participants") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList()
                        val otroId = participants.firstOrNull { it != myUid } ?: ""

                        @Suppress("UNCHECKED_CAST")
                        val infoOtro = (doc.get("info") as? Map<String, Any?>)
                            ?.get(otroId) as? Map<String, Any?>

                        val legacyOk = doc.getString("amigoId") == otroId
                        val nombre = (infoOtro?.get("nombre") as? String)
                            ?: doc.getString("amigoNombre")?.takeIf { legacyOk }
                            ?: "Usuario"
                        val foto = (infoOtro?.get("fotoUrl") as? String)
                            ?: doc.getString("fotoUrl")?.takeIf { legacyOk }

                        escucharMensajes(doc.id)

                        Chat(
                            id = doc.id,
                            amigo = Amigo(otroId, nombre, "", foto),
                            ultimoMensaje = doc.getString("ultimoMensaje") ?: "",
                            fechaUltimoMensaje = doc.getString("fecha") ?: "Ahora",
                            mensajes = actuales[doc.id]?.mensajes ?: emptyList()
                        )
                    }
                _chats.value = lista
            }
    }

    private fun escucharMensajes(chatId: String) {
        if (messageListeners.containsKey(chatId)) return
        messageListeners[chatId] = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando mensajes de $chatId", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val mensajes = snapshot.documents.map { doc ->
                    Mensaje(
                        id = doc.id,
                        emisorId = doc.getString("emisorId") ?: "",
                        emisorNombre = doc.getString("emisorNombre") ?: "",
                        texto = doc.getString("texto") ?: "",
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                }
                _chats.value = _chats.value.map { chat ->
                    if (chat.id == chatId) {
                        chat.copy(
                            mensajes = mensajes,
                            ultimoMensaje = mensajes.lastOrNull()?.texto ?: chat.ultimoMensaje
                        )
                    } else chat
                }
            }
    }

    override fun agregarAmigo(amigo: Amigo) {
        if (myUid.isBlank()) return
        if (_amigos.value.none { it.id == amigo.id }) {
            _amigos.value = _amigos.value + amigo
        }
        firestore.collection("users").document(myUid)
            .collection("amigos").document(amigo.id)
            .set(
                mapOf(
                    "id" to amigo.id,
                    "nombre" to amigo.nombre,
                    "correo" to amigo.correo,
                    "fotoUrl" to amigo.fotoUrl,
                    "timestamp" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { Log.e(TAG, "No se pudo agregar amigo", it) }
    }

    override fun esAmigo(usuarioId: String): Boolean =
        _amigos.value.any { it.id == usuarioId }

    override fun obtenerOCrearChatCon(amigo: Amigo): Chat {
        val chatId = if (myUid < amigo.id) "chat_${myUid}_${amigo.id}" else "chat_${amigo.id}_${myUid}"
        Log.d(TAG, "obtenerOCrearChatCon: myUid='$myUid' amigo='${amigo.id}' chatId='$chatId'")

        if (myUid.isBlank()) {
            Log.e(TAG, "myUid está vacío: falta llamar a chatRepository.iniciarSesion(...) tras el login (ver Nav3Host)")
            return Chat(id = chatId, amigo = amigo, ultimoMensaje = "", fechaUltimoMensaje = "Ahora")
        }

        _chats.value.find { it.id == chatId }?.let {
            Log.d(TAG, "El chat $chatId ya existía localmente; no se vuelve a escribir")
            return it
        }

        val nuevoChat = Chat(
            id = chatId,
            amigo = amigo,
            ultimoMensaje = "Conversación iniciada",
            fechaUltimoMensaje = "Ahora",
            mensajes = emptyList()
        )
        _chats.value = _chats.value + nuevoChat
        agregarAmigo(amigo)
        escucharMensajes(chatId)

        firestore.collection("chats").document(chatId)
            .set(
                mapOf(
                    "id" to chatId,
                    "participants" to listOf(myUid, amigo.id),
                    "info" to mapOf(
                        myUid to mapOf("nombre" to myName, "fotoUrl" to myPhoto),
                        amigo.id to mapOf("nombre" to amigo.nombre, "fotoUrl" to amigo.fotoUrl)
                    ),
                    "lastUpdated" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { Log.d(TAG, "Chat $chatId creado en Firestore") }
            .addOnFailureListener { Log.e(TAG, "No se pudo crear el chat $chatId", it) }

        return nuevoChat
    }

    override fun enviarMensaje(chatId: String, texto: String, emisorId: String, emisorNombre: String) {
        val limpio = texto.trim()
        if (limpio.isEmpty() || myUid.isBlank()) return

        val chatRef = firestore.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()
        val ahora = System.currentTimeMillis()

        firestore.batch().apply {
            set(
                msgRef,
                mapOf(
                    "emisorId" to myUid,
                    "emisorNombre" to myName,
                    "texto" to limpio,
                    "timestamp" to ahora
                )
            )
            set(
                chatRef,
                mapOf(
                    "ultimoMensaje" to limpio,
                    "fecha" to "Ahora",
                    "lastUpdated" to ahora
                ),
                SetOptions.merge()
            )
        }.commit()
            .addOnSuccessListener { Log.d(TAG, "Mensaje enviado a $chatId") }
            .addOnFailureListener { Log.e(TAG, "No se pudo enviar el mensaje a $chatId", it) }
    }
}