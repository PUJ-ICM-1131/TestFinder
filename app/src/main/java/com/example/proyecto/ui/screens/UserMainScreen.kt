package com.example.proyecto.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.proyecto.R
import com.example.proyecto.data.model.Amigo
import com.example.proyecto.data.repository.AuthRepository
import com.example.proyecto.data.repository.ChatRepository
import com.example.proyecto.data.repository.MockChatRepository
import com.example.proyecto.data.repository.TestRepository
import com.example.proyecto.ui.components.UserDetailDialog
import com.example.proyecto.ui.screens.tabs.ChatsTab
import com.example.proyecto.ui.screens.tabs.EnCursoTab
import com.example.proyecto.ui.screens.tabs.ExplorarTab
import com.example.proyecto.ui.screens.tabs.HistorialTab
import com.example.proyecto.ui.screens.tabs.PerfilTab

sealed class BottomTab(val titleRes: Int, val icon: ImageVector) {
    data object Explorar : BottomTab(R.string.tab_explorar, Icons.Default.Explore)
    data object EnCurso : BottomTab(R.string.tab_en_curso, Icons.AutoMirrored.Filled.DirectionsRun)
    data object Chats : BottomTab(R.string.tab_chats, Icons.AutoMirrored.Filled.Chat)
    data object Historial : BottomTab(R.string.tab_historial, Icons.Default.History)
    data object Perfil : BottomTab(R.string.tab_perfil, Icons.Default.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMainScreen(
    authRepository: AuthRepository,
    testRepository: TestRepository,
    chatRepository: ChatRepository = remember { MockChatRepository() },
    initialFullMapTestId: String? = null,
    onSelectTestDetail: (String) -> Unit,
    onOpenFullMap: (String) -> Unit = {},
    onLogout: () -> Unit,
) {
    var selectedTabItem by remember { mutableIntStateOf(if (initialFullMapTestId != null) 1 else 0) }
    var activeFullMapTestId by remember(initialFullMapTestId) { mutableStateOf(initialFullMapTestId) }

    val tabs = listOf(
        BottomTab.Explorar,
        BottomTab.EnCurso,
        BottomTab.Chats,
        BottomTab.Historial,
        BottomTab.Perfil
    )

    var targetUserForDialog by remember { mutableStateOf<Amigo?>(null) }
    var targetChatIdToOpen by remember { mutableStateOf<String?>(null) }

    val amigos by chatRepository.amigos.collectAsState()

    Scaffold(
        topBar = {
            if (activeFullMapTestId == null) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedTabItem) {
                                0 -> stringResource(R.string.header_explorar)
                                1 -> stringResource(R.string.header_en_curso)
                                2 -> stringResource(R.string.header_chats)
                                3 -> stringResource(R.string.header_historial)
                                else -> stringResource(R.string.header_perfil)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    val tabLabel = stringResource(tab.titleRes)
                    NavigationBarItem(
                        selected = (selectedTabItem == index) && (activeFullMapTestId == null),
                        onClick = {
                            selectedTabItem = index
                            activeFullMapTestId = null
                        },
                        icon = { Icon(tab.icon, contentDescription = tabLabel) },
                        label = { Text(tabLabel) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (activeFullMapTestId != null) {
                MapScreen(
                    testId = activeFullMapTestId!!,
                    testRepository = testRepository,
                    authRepository = authRepository,
                    chatRepository = chatRepository,
                    onBack = { activeFullMapTestId = null }
                )
            } else {
                when (selectedTabItem) {
                    0 -> ExplorarTab(
                        testRepository = testRepository,
                        onTestClick = onSelectTestDetail
                    )
                    1 -> EnCursoTab(
                        testRepository = testRepository,
                        onOpenFullMap = { testId ->
                            activeFullMapTestId = testId
                            onOpenFullMap(testId)
                        }
                    )
                    2 -> ChatsTab(
                        chatRepository = chatRepository,
                        selectedChatId = targetChatIdToOpen,
                        onChatClosed = { targetChatIdToOpen = null }
                    )
                    3 -> HistorialTab(
                        testRepository = testRepository
                    )
                    4 -> PerfilTab(
                        authRepository = authRepository,
                        onLogout = onLogout
                    )
                }
            }
        }

        targetUserForDialog?.let { amigo ->
            val esAmigo = amigos.any { it.id == amigo.id }
            UserDetailDialog(
                usuarioId = amigo.id,
                nombre = amigo.nombre,
                fotoUrl = amigo.fotoUrl,
                esAmigo = esAmigo,
                onAgregarAmigo = {
                    chatRepository.agregarAmigo(it)
                },
                onIniciarChat = { amigoParaChat ->
                    val chat = chatRepository.obtenerOCrearChatCon(amigoParaChat)
                    targetChatIdToOpen = chat.id
                    selectedTabItem = 2
                    activeFullMapTestId = null
                },
                onDismiss = { targetUserForDialog = null }
            )
        }
    }
}
