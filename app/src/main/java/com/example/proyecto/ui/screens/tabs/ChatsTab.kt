package com.example.proyecto.ui.screens.tabs

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.proyecto.data.model.Amigo
import com.example.proyecto.data.model.Chat
import com.example.proyecto.data.repository.ChatRepository
import com.example.proyecto.ui.components.NotificationHelper

@Composable
fun ChatsTab(
    chatRepository: ChatRepository,
    selectedChatId: String? = null,
    onChatClosed: () -> Unit = {}
) {
    val context = LocalContext.current
    val amigos by chatRepository.amigos.collectAsState()
    val chats by chatRepository.chats.collectAsState()
    val todosLosPerfiles by chatRepository.todosLosPerfiles.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeChatId by remember(selectedChatId) { mutableStateOf(selectedChatId) }

    val activeChat = remember(activeChatId, chats) {
        chats.find { it.id == activeChatId }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: android.net.Uri? ->
        contactUri?.let { uri ->
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                        val contactName = if (nameIdx >= 0) cursor.getString(nameIdx) else "Contacto"
                        val nuevoAmigo = Amigo(
                            id = "contact_${System.currentTimeMillis()}",
                            nombre = contactName,
                            correo = "${contactName.lowercase().replace(" ", "")}@contacto.com"
                        )
                        chatRepository.agregarAmigo(nuevoAmigo)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var lastKnownMessageCount by remember { mutableIntStateOf(0) }
    val totalMessages = chats.sumOf { it.mensajes.size }

    LaunchedEffect(totalMessages) {
        if (lastKnownMessageCount > 0 && totalMessages > lastKnownMessageCount) {
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "u1"
            val latestIncomingMessage = chats.flatMap { it.mensajes }
                .filter { it.emisorId != currentUid && it.emisorId != "u1" }
                .maxByOrNull { it.timestamp }

            if (latestIncomingMessage != null && (System.currentTimeMillis() - latestIncomingMessage.timestamp < 10000L)) {
                NotificationHelper.showNotification(
                    context = context,
                    title = "Nuevo mensaje de ${latestIncomingMessage.emisorNombre.ifEmpty { "Usuario" }}",
                    message = latestIncomingMessage.texto
                )
            }
        }
        lastKnownMessageCount = totalMessages
    }

    val perfilesFiltrados = remember(searchQuery, todosLosPerfiles) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            todosLosPerfiles.filter { amigo ->
                amigo.nombre.contains(searchQuery, ignoreCase = true) ||
                amigo.correo.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (activeChat != null) {
        ChatConversationScreen(
            chat = activeChat,
            onSendMessage = { texto ->
                chatRepository.enviarMensaje(activeChat.id, texto, "u1", "Yo")
            },
            onBack = {
                activeChatId = null
                onChatClosed()
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Buscador de perfiles
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar perfil o usuario para escribirle...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            if (searchQuery.isNotBlank()) {
                Text(
                    text = "Resultados de la búsqueda (${perfilesFiltrados.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (perfilesFiltrados.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No se encontraron usuarios con ese nombre o correo.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(perfilesFiltrados) { perfil ->
                            SearchResultUserRow(
                                amigo = perfil,
                                onClick = {
                                    val chat = chatRepository.obtenerOCrearChatCon(perfil)
                                    activeChatId = chat.id
                                    searchQuery = ""
                                }
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Amigos (${amigos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { contactPickerLauncher.launch(null) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Contactos"
                        )
                    }
                }

                if (amigos.isEmpty()) {
                    Text(
                        text = "Aún no has agregado amigos. Usa el buscador de arriba para encontrar usuarios y escribirles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        items(amigos) { amigo ->
                            FriendAvatarItem(
                                amigo = amigo,
                                onClick = {
                                    val chat = chatRepository.obtenerOCrearChatCon(amigo)
                                    activeChatId = chat.id
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Conversaciones Recientes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (chats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No tienes conversaciones activas aún.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(chats) { chat ->
                            ChatItemRow(
                                chat = chat,
                                onClick = { activeChatId = chat.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultUserRow(
    amigo: Amigo,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val photo = if (!amigo.fotoUrl.isNullOrBlank()) amigo.fotoUrl else com.example.proyecto.data.model.DEFAULT_PROFILE_PICTURE
                AsyncImage(
                    model = photo,
                    contentDescription = amigo.nombre,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = amigo.nombre,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (amigo.correo.isNotBlank()) {
                    Text(
                        text = amigo.correo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Escribir", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun FriendAvatarItem(
    amigo: Amigo,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val photo = if (!amigo.fotoUrl.isNullOrBlank()) amigo.fotoUrl else com.example.proyecto.data.model.DEFAULT_PROFILE_PICTURE
            AsyncImage(
                model = photo,
                contentDescription = amigo.nombre,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = amigo.nombre.split(" ").firstOrNull() ?: amigo.nombre,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
fun ChatItemRow(
    chat: Chat,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                val photo = if (!chat.amigo.fotoUrl.isNullOrBlank()) chat.amigo.fotoUrl else com.example.proyecto.data.model.DEFAULT_PROFILE_PICTURE
                AsyncImage(
                    model = photo,
                    contentDescription = chat.amigo.nombre,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.amigo.nombre,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = chat.ultimoMensaje.ifEmpty { "Inicia la conversación..." },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Text(
                text = chat.fechaUltimoMensaje,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    chat: Chat,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    var mensajeTexto by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val photo = if (!chat.amigo.fotoUrl.isNullOrBlank()) chat.amigo.fotoUrl else com.example.proyecto.data.model.DEFAULT_PROFILE_PICTURE
                            AsyncImage(
                                model = photo,
                                contentDescription = chat.amigo.nombre,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = chat.amigo.nombre.ifEmpty { "Usuario" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (chat.amigo.correo.isNotBlank()) {
                                Text(
                                    text = chat.amigo.correo,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "u1"
                items(chat.mensajes) { mensaje ->
                    val esMio = (mensaje.emisorId == currentUid || mensaje.emisorId == "u1")
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (esMio) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (esMio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.width(260.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = mensaje.texto,
                                    color = if (esMio) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = mensajeTexto,
                    onValueChange = { mensajeTexto = it },
                    placeholder = { Text("Escribe un mensaje...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (mensajeTexto.isNotBlank()) {
                            onSendMessage(mensajeTexto)
                            mensajeTexto = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
