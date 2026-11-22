package com.example.proyecto.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.proyecto.R
import com.example.proyecto.data.model.Amigo
import com.example.proyecto.data.repository.AuthRepository
import com.example.proyecto.data.repository.ChatRepository
import com.example.proyecto.data.repository.FirebaseChatRepository
import com.example.proyecto.data.repository.LocationRepository
import com.example.proyecto.data.repository.MockAuthRepository
import com.example.proyecto.data.repository.TestRepository
import com.example.proyecto.ui.components.CustomMarkerHelper
import com.example.proyecto.ui.components.UserDetailDialog
import com.example.proyecto.ui.components.fetchStreetRoute
import com.example.proyecto.ui.components.rememberSensorDataState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val CurrentUserRouteColor = Color(0xFF00BCD4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    testId: String,
    testRepository: TestRepository,
    authRepository: AuthRepository = remember { MockAuthRepository() },
    chatRepository: ChatRepository = remember { FirebaseChatRepository() },
    locationRepository: LocationRepository = remember { LocationRepository() },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by authRepository.authState.collectAsState()

    val deviceId = remember(context) {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrBlank()) "dev_$androidId" else "user_" + java.util.UUID.randomUUID().toString().take(6)
    }

    val currentUid = authState.usuario?.id?.let {
        if (it == "u_prueba" || it == "u1") "${it}_$deviceId" else it
    } ?: authState.empresa?.id?.let {
        if (it == "e_prueba" || it == "e1") "${it}_$deviceId" else it
    } ?: deviceId

    val currentName = authState.usuario?.nombre ?: authState.empresa?.nombre ?: "Usuario (${deviceId.takeLast(4)})"
    val currentEmail = authState.usuario?.correo ?: authState.empresa?.correo ?: "$deviceId@dispositivo.com"
    val currentPhotoUrl = authState.usuario?.fotoUrl

    var isConnected by remember { mutableStateOf(true) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var currentUserLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasCenteredCamera by remember { mutableStateOf(false) }
    var targetUserForDialog by remember { mutableStateOf<Amigo?>(null) }

    val amigos by chatRepository.amigos.collectAsState()
    val onlineUsers by locationRepository.getOnlineUsers().collectAsState(initial = emptyList())

    val todosLosTests by testRepository.todosLosTests.collectAsState()
    val test = todosLosTests.find { it.id == testId } ?: todosLosTests.firstOrNull()

    val sensorData = rememberSensorDataState()

    val destLat = test?.latitud ?: 4.6698
    val destLng = test?.longitud ?: -74.0532
    val destLatLng = LatLng(destLat, destLng)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(destLatLng, 14f)
    }

    val markerIcons = remember { mutableStateMapOf<String, BitmapDescriptor>() }
    val streetRoutes = remember { mutableStateMapOf<String, List<LatLng>>() }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    fun fetchInitialLocation() {
        if (hasLocationPermission) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && currentUserLocation == null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    currentUserLocation = latLng
                }
            }
        }
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    currentUserLocation = latLng
                    if (isConnected) {
                        scope.launch {
                            locationRepository.updateLocation(
                                uid = currentUid,
                                name = currentName,
                                email = currentEmail,
                                lat = location.latitude,
                                lng = location.longitude,
                                photoUrl = currentPhotoUrl
                            )
                        }
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        hasLocationPermission = fineGranted || coarseGranted
        if (hasLocationPermission) {
            isConnected = true
            fetchInitialLocation()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            fetchInitialLocation()
        }
    }

    // Auto-center camera on user location when first obtained
    LaunchedEffect(currentUserLocation) {
        currentUserLocation?.let { location ->
            if (!hasCenteredCamera) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(location, 15f)
                )
                hasCenteredCamera = true
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (hasLocationPermission) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    DisposableEffect(isConnected) {
        if (isConnected) {
            startLocationUpdates()
            scope.launch { locationRepository.setConnectedState(currentUid, true) }
        } else {
            stopLocationUpdates()
            scope.launch { locationRepository.setConnectedState(currentUid, false) }
        }
        onDispose {
            stopLocationUpdates()
            scope.launch { locationRepository.setConnectedState(currentUid, false) }
        }
    }

    // Heartbeat to continuously send location & timestamp updates so map stays fresh for all users
    LaunchedEffect(isConnected, currentUserLocation) {
        if (isConnected && currentUserLocation != null) {
            while (isActive) {
                locationRepository.updateLocation(
                    uid = currentUid,
                    name = currentName,
                    email = currentEmail,
                    lat = currentUserLocation!!.latitude,
                    lng = currentUserLocation!!.longitude,
                    photoUrl = currentPhotoUrl
                )
                delay(3000L)
            }
        }
    }

    LaunchedEffect(currentName, currentPhotoUrl) {
        val descriptorYo = CustomMarkerHelper.createCustomMarkerBitmap(
            context = context,
            imageUrl = currentPhotoUrl,
            userName = currentName,
            isCurrentUser = true
        )
        markerIcons[currentUid] = descriptorYo
    }

    val fallbackUserText = stringResource(R.string.user_fallback)
    val fallbackTesterText = stringResource(R.string.user_tester_fallback)

    LaunchedEffect(onlineUsers, fallbackUserText) {
        onlineUsers.forEach { user ->
            if (user.uid != currentUid) {
                scope.launch {
                    val descriptor = CustomMarkerHelper.createCustomMarkerBitmap(
                        context = context,
                        imageUrl = user.photoUrl,
                        userName = user.name.ifEmpty { fallbackUserText },
                        isCurrentUser = false
                    )
                    markerIcons[user.uid] = descriptor
                }
            }
        }
    }

    // Fetch street-following routes between current user and other online users
    LaunchedEffect(currentUserLocation, onlineUsers) {
        val myLoc = currentUserLocation ?: return@LaunchedEffect
        onlineUsers.filter { it.uid != currentUid }.forEach { user ->
            val userLat = user.latitude
            val userLng = user.longitude
            if (userLat != null && userLng != null) {
                val userDest = LatLng(userLat, userLng)
                scope.launch {
                    val routePoints = fetchStreetRoute(myLoc, userDest)
                    streetRoutes[user.uid] = routePoints
                }
            }
        }
    }

    if (test == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.test_not_found))
        }
        return
    }

    val currentUserMarkerState = remember { MarkerState(position = LatLng(0.0, 0.0)) }
    LaunchedEffect(currentUserLocation) {
        currentUserLocation?.let {
            currentUserMarkerState.position = it
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isConnected)
                                stringResource(R.string.switch_connected)
                            else
                                stringResource(R.string.switch_disconnected),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isConnected,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (hasLocationPermission) {
                                        isConnected = true
                                    } else {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                } else {
                                    isConnected = false
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = hasLocationPermission,
                    mapType = MapType.NORMAL
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true)
            ) {
                if (isConnected && currentUserLocation != null) {
                    Marker(
                        state = currentUserMarkerState,
                        title = stringResource(R.string.marker_title_you, currentName),
                        snippet = stringResource(R.string.switch_connected),
                        icon = markerIcons[currentUid]
                    )
                }

                Marker(
                    state = remember { MarkerState(position = destLatLng) },
                    title = test.nombre,
                    snippet = test.direccion
                )

                // Render ALL real-time online users from Firestore and connect street routes with current user
                onlineUsers.filter { it.uid != currentUid }.forEach { user ->
                    val lat = user.latitude
                    val lng = user.longitude

                    if (lat != null && lng != null) {
                        val position = LatLng(lat, lng)

                        if (isConnected && currentUserLocation != null) {
                            val routePoints = streetRoutes[user.uid] ?: listOf(currentUserLocation!!, position)
                            Polyline(
                                points = routePoints,
                                color = CurrentUserRouteColor,
                                width = 8f
                            )
                        }

                        val userMarkerState = remember(user.uid) { MarkerState(position = position) }
                        LaunchedEffect(position) {
                            userMarkerState.position = position
                        }

                        Marker(
                            state = userMarkerState,
                            title = user.name.ifEmpty { fallbackTesterText },
                            snippet = stringResource(R.string.switch_connected),
                            icon = markerIcons[user.uid],
                            onClick = {
                                targetUserForDialog = Amigo(
                                    id = user.uid,
                                    nombre = user.name.ifEmpty { fallbackUserText },
                                    correo = user.email,
                                    fotoUrl = user.photoUrl
                                )
                                true
                            }
                        )
                    }
                }
            }

            // Status Banner (taller style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        if (isConnected) Color(0xCC2E7D32) else Color(0xCC424242)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isConnected)
                        stringResource(R.string.location_status_on)
                    else
                        stringResource(R.string.location_status_off),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Compass overlay
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp)
                    .size(56.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = stringResource(R.string.compass_description),
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(sensorData.azimuthDegrees)
                    )
                    Text(
                        text = "${sensorData.azimuthDegrees.toInt()}°",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }
            }

            // Driving warning
            AnimatedVisibility(
                visible = sensorData.speedWarning,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 50.dp, start = 80.dp, end = 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFD32F2F),
                    contentColor = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.driving_warning_msg),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
            onAgregarAmigo = { chatRepository.agregarAmigo(it) },
            onIniciarChat = { amigoParaChat ->
                chatRepository.obtenerOCrearChatCon(amigoParaChat)
                onBack()
            },
            onDismiss = { targetUserForDialog = null }
        )
    }
}
