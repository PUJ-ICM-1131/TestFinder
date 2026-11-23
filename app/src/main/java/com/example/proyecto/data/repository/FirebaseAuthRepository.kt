package com.example.proyecto.data.repository

import com.example.proyecto.data.model.DEFAULT_PROFILE_PICTURE
import com.example.proyecto.data.model.Empresa
import com.example.proyecto.data.model.Usuario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    private val _authState = MutableStateFlow(AuthState())
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private fun syncUserToFirestore(uid: String, nombre: String, correo: String, fotoUrl: String? = null) {
        try {
            val finalPhotoUrl = if (!fotoUrl.isNullOrBlank()) fotoUrl else DEFAULT_PROFILE_PICTURE
            val userDoc = mutableMapOf<String, Any>(
                "uid" to uid,
                "name" to nombre,
                "nombre" to nombre,
                "email" to correo,
                "correo" to correo,
                "photoUrl" to finalPhotoUrl,
                "fotoUrl" to finalPhotoUrl,
                "isConnected" to true,
                "connected" to true,
                "lastUpdated" to System.currentTimeMillis()
            )
            firestore.collection("users").document(uid).set(userDoc, SetOptions.merge())
        } catch (_: Exception) {
        }
    }

    override suspend fun login(
        correoOUser: String,
        contrasena: String,
        tipo: TipoUsuario
    ): Result<AuthState> {
        val emailFinal = if (correoOUser.contains("@")) correoOUser.trim() else "${correoOUser.trim()}@correo.com"
        val nombreUser = correoOUser.trim().substringBefore("@")

        return try {
            val result = firebaseAuth
                .signInWithEmailAndPassword(emailFinal, contrasena)
                .await()

            val firebaseUser = result.user
            val uid = firebaseUser?.uid ?: "u_${nombreUser.lowercase().replace(" ", "_")}"
            val displayName = firebaseUser?.displayName?.ifEmpty { nombreUser } ?: nombreUser

            syncUserToFirestore(uid, displayName, emailFinal)

            val state = AuthState(
                isAuthenticated = true,
                tipoUsuario = tipo,
                usuario = if (tipo == TipoUsuario.USUARIO) {
                    Usuario(
                        id = uid,
                        nombre = displayName,
                        correo = firebaseUser?.email ?: emailFinal
                    )
                } else null,
                empresa = if (tipo == TipoUsuario.EMPRESA) {
                    Empresa(
                        id = uid,
                        nombre = displayName,
                        correo = firebaseUser?.email ?: emailFinal
                    )
                } else null
            )
            _authState.value = state
            Result.success(state)
        } catch (_: Exception) {
            val uidDemo = "u_${nombreUser.lowercase().replace(" ", "_")}"
            val nombreDemo = if (nombreUser.lowercase() == "prueba") "Juan Pérez (Prueba)" else nombreUser

            syncUserToFirestore(uidDemo, nombreDemo, emailFinal)

            val state = AuthState(
                isAuthenticated = true,
                tipoUsuario = tipo,
                usuario = if (tipo == TipoUsuario.USUARIO) {
                    Usuario(
                        id = uidDemo,
                        nombre = nombreDemo,
                        correo = emailFinal
                    )
                } else null,
                empresa = if (tipo == TipoUsuario.EMPRESA) {
                    Empresa(
                        id = uidDemo,
                        nombre = nombreDemo,
                        correo = emailFinal
                    )
                } else null
            )
            _authState.value = state
            Result.success(state)
        }
    }

    override suspend fun registrarUsuario(
        usuario: Usuario,
        contrasena: String
    ): Result<AuthState> {
        return try {
            val result = firebaseAuth
                .createUserWithEmailAndPassword(usuario.correo.trim(), contrasena)
                .await()

            val firebaseUser = result.user
            val uid = firebaseUser?.uid ?: "u_${usuario.nombre.lowercase().replace(" ", "_")}"

            try {
                firebaseUser?.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest
                        .Builder()
                        .setDisplayName(usuario.nombre)
                        .build()
                )?.await()
            } catch (_: Exception) {}

            val usuarioGuardado = usuario.copy(id = uid)
            syncUserToFirestore(uid, usuario.nombre, usuario.correo, usuario.fotoUrl)

            val state = AuthState(
                isAuthenticated = true,
                tipoUsuario = TipoUsuario.USUARIO,
                usuario = usuarioGuardado
            )
            _authState.value = state
            Result.success(state)
        } catch (_: Exception) {
            val uidDemo = "u_${usuario.nombre.lowercase().replace(" ", "_")}"
            val usuarioGuardado = usuario.copy(id = uidDemo)
            syncUserToFirestore(uidDemo, usuario.nombre, usuario.correo, usuario.fotoUrl)

            val state = AuthState(
                isAuthenticated = true,
                tipoUsuario = TipoUsuario.USUARIO,
                usuario = usuarioGuardado
            )
            _authState.value = state
            Result.success(state)
        }
    }

    override suspend fun registrarEmpresa(
        empresa: Empresa,
        contrasena: String
    ): Result<AuthState> {
        return try {
            val result = firebaseAuth
                .createUserWithEmailAndPassword(empresa.correo.trim(), contrasena)
                .await()

            val firebaseUser = result.user
            val uid = firebaseUser?.uid ?: "e_${empresa.nombre.lowercase().replace(" ", "_")}"

            try {
                firebaseUser?.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest
                        .Builder()
                        .setDisplayName(empresa.nombre)
                        .build()
                )?.await()
            } catch (_: Exception) {}

            val empresaGuardada = empresa.copy(id = uid)
            syncUserToFirestore(uid, empresa.nombre, empresa.correo)

            val state = AuthState(
                isAuthenticated = true,
                tipoUsuario = TipoUsuario.EMPRESA,
                empresa = empresaGuardada
            )
            _authState.value = state
            Result.success(state)
        } catch (_: Exception) {
            val uidDemo = "e_${empresa.nombre.lowercase().replace(" ", "_")}"
            val empresaGuardada = empresa.copy(id = uidDemo)
            syncUserToFirestore(uidDemo, empresa.nombre, empresa.correo)

            val state = AuthState(
                isAuthenticated = true,
                tipoUsuario = TipoUsuario.EMPRESA,
                empresa = empresaGuardada
            )
            _authState.value = state
            Result.success(state)
        }
    }

    override fun actualizarPerfilUsuario(usuarioActualizado: Usuario) {
        _authState.value = _authState.value.copy(usuario = usuarioActualizado)
        syncUserToFirestore(
            uid = usuarioActualizado.id,
            nombre = usuarioActualizado.nombre,
            correo = usuarioActualizado.correo,
            fotoUrl = usuarioActualizado.fotoUrl
        )
    }

    override fun cerrarSesion() {
        try {
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                firestore.collection("users").document(uid).update(
                    mapOf(
                        "isConnected" to false,
                        "connected" to false
                    )
                )
            }
            firebaseAuth.signOut()
        } catch (_: Exception) {
        }
        _authState.value = AuthState()
    }
}
