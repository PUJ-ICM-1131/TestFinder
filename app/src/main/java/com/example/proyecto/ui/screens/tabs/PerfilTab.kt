package com.example.proyecto.ui.screens.tabs

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.proyecto.R
import com.example.proyecto.data.model.Usuario
import com.example.proyecto.data.repository.AuthRepository
import com.example.proyecto.data.repository.CloudinaryUploader
import com.example.proyecto.data.repository.LocationRepository
import com.example.proyecto.ui.components.PhotoPickerDialog
import kotlinx.coroutines.launch

@Composable
fun PerfilTab(
    authRepository: AuthRepository,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by authRepository.authState.collectAsState()
    val usuario = authState.usuario ?: Usuario()

    var nombre by remember(usuario) { mutableStateOf(usuario.nombre) }
    var edad by remember(usuario) { mutableStateOf(usuario.edad.toString()) }
    var fechaNacimiento by remember(usuario) { mutableStateOf(usuario.fechaNacimiento) }
    var estrato by remember(usuario) { mutableFloatStateOf(usuario.estrato.toFloat()) }
    var direccion by remember(usuario) { mutableStateOf(usuario.direccion) }
    var correo by remember(usuario) { mutableStateOf(usuario.correo) }
    var telefono by remember(usuario) { mutableStateOf(usuario.telefono) }
    var fotoUrl by remember(usuario) { mutableStateOf(usuario.fotoUrl) }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var mensajeExito by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            fotoUrl = it.toString()
            mensajeExito = false
        }
    }

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempPhotoUri != null) {
            selectedImageUri = tempPhotoUri
            fotoUrl = tempPhotoUri.toString()
            mensajeExito = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val photoFile = java.io.File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempPhotoUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        )
        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val photoFile = java.io.File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempPhotoUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    val defaultTitle = stringResource(R.string.profile_title)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .size(100.dp)
                .clip(CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable { showPhotoDialog = true },
            contentAlignment = Alignment.Center
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            } else {
                val finalUrl = if (!fotoUrl.isNullOrBlank()) fotoUrl else com.example.proyecto.data.model.DEFAULT_PROFILE_PICTURE
                AsyncImage(
                    model = finalUrl,
                    contentDescription = "Foto de Perfil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Text(
            text = nombre.ifBlank { defaultTitle },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.profile_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                OutlinedTextField(
                    value = nombre,
                    onValueChange = {
                        nombre = it
                        mensajeExito = false
                    },
                    label = { Text(stringResource(R.string.field_full_name)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = edad,
                        onValueChange = {
                            edad = it
                            mensajeExito = false
                        },
                        label = { Text(stringResource(R.string.field_age)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = fechaNacimiento,
                        onValueChange = {
                            fechaNacimiento = it
                            mensajeExito = false
                        },
                        label = { Text(stringResource(R.string.field_birth_date)) },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        modifier = Modifier
                            .weight(1.5f)
                            .padding(start = 4.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.field_stratum, estrato.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = estrato,
                        onValueChange = {
                            estrato = it
                            mensajeExito = false
                        },
                        valueRange = 1f..6f,
                        steps = 4
                    )
                }

                OutlinedTextField(
                    value = direccion,
                    onValueChange = {
                        direccion = it
                        mensajeExito = false
                    },
                    label = { Text(stringResource(R.string.field_address)) },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = correo,
                    onValueChange = {
                        correo = it
                        mensajeExito = false
                    },
                    label = { Text(stringResource(R.string.field_email)) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = telefono,
                    onValueChange = {
                        telefono = it
                        mensajeExito = false
                    },
                    label = { Text(stringResource(R.string.field_phone)) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (mensajeExito) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.profile_updated_success),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                isSaving = true
                                var finalUrl = fotoUrl

                                val uriToUpload = selectedImageUri ?: if (!finalUrl.isNullOrBlank() && (finalUrl.startsWith("content://") || finalUrl.startsWith("file://"))) {
                                    Uri.parse(finalUrl)
                                } else null

                                if (uriToUpload != null) {
                                    finalUrl = CloudinaryUploader.uploadImage(context, uriToUpload)
                                    fotoUrl = finalUrl
                                    selectedImageUri = null
                                }

                                val usuarioActualizado = usuario.copy(
                                    nombre = nombre,
                                    edad = edad.toIntOrNull() ?: usuario.edad,
                                    fechaNacimiento = fechaNacimiento,
                                    estrato = estrato.toInt(),
                                    direccion = direccion,
                                    correo = correo,
                                    telefono = telefono,
                                    fotoUrl = finalUrl
                                )

                                authRepository.actualizarPerfilUsuario(usuarioActualizado)

                                LocationRepository().updateLocation(
                                    uid = usuario.id,
                                    name = nombre.ifEmpty { "Usuario" },
                                    email = correo,
                                    lat = 4.6097,
                                    lng = -74.0817,
                                    photoUrl = finalUrl
                                )

                                mensajeExito = true
                                Toast.makeText(context, "Perfil guardado y foto subida a Cloudinary", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al subir foto a Cloudinary: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardando en Cloudinary...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_save_changes), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                authRepository.cerrarSesion()
                onLogout()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_close_session), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showPhotoDialog) {
        PhotoPickerDialog(
            onDismissRequest = { showPhotoDialog = false },
            onSelectCamera = { launchCamera() },
            onSelectGallery = { galleryLauncher.launch("image/*") }
        )
    }
}
