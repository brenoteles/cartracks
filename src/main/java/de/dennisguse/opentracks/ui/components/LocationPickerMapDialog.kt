package de.dennisguse.opentracks.ui.components

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.dennisguse.opentracks.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun LocationPickerMapDialog(
    title: String,
    initialLat: Double,
    initialLon: Double,
    currentGps: Pair<Double, Double>?,
    onConfirm: (lat: Double, lon: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var pinnedLat by remember { mutableStateOf(initialLat) }
    var pinnedLon by remember { mutableStateOf(initialLon) }

    var searchQuery by remember { mutableStateOf("") }
    var resolvedAddress by remember { mutableStateOf<String?>("Carregando endereço...") }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Helper: Reverse Geocode (Lat/Lon -> Readable Street Address)
    fun updateReverseGeocode(lat: Double, lon: Double) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val list = geocoder.getFromLocation(lat, lon, 1)
                    withContext(Dispatchers.Main) {
                        if (!list.isNullOrEmpty()) {
                            val addr = list[0]
                            val thoroughfare = addr.thoroughfare
                            val subThoroughfare = addr.subThoroughfare
                            val subLocality = addr.subLocality
                            val locality = addr.locality ?: addr.subAdminArea ?: ""

                            val formatted = if (thoroughfare != null) {
                                if (subThoroughfare != null) "$thoroughfare, $subThoroughfare - $subLocality, $locality"
                                else "$thoroughfare - $subLocality, $locality"
                            } else {
                                addr.getAddressLine(0) ?: "$locality"
                            }
                            resolvedAddress = formatted
                        } else {
                            resolvedAddress = "Localização no mapa"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resolvedAddress = "Coordenadas: %.5f, %.5f".format(Locale.US, lat, lon)
                }
            }
        }
    }

    // Helper: Forward Geocode (Search Query String -> Lat/Lon)
    fun performSearch() {
        if (searchQuery.isBlank()) return
        focusManager.clearFocus()
        isSearching = true
        searchError = null

        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocationName(searchQuery, 1)
                    withContext(Dispatchers.Main) {
                        isSearching = false
                        if (!results.isNullOrEmpty()) {
                            val addr = results[0]
                            pinnedLat = addr.latitude
                            pinnedLon = addr.longitude
                            resolvedAddress = addr.getAddressLine(0) ?: searchQuery
                            searchError = null
                        } else {
                            searchError = "Nenhum endereço encontrado para: \"$searchQuery\""
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isSearching = false
                        searchError = "Serviço de busca de endereço indisponível no dispositivo."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSearching = false
                    searchError = "Erro na busca: ${e.localizedMessage ?: "Verifique sua conexão."}"
                }
            }
        }
    }

    // Initialize address lookup for starting point
    LaunchedEffect(Unit) {
        updateReverseGeocode(pinnedLat, pinnedLon)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WandaBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. High Definition Interactive Map Engine
            CarMapView(
                pinnedLocation = Pair(pinnedLat, pinnedLon),
                pinnedTitle = title,
                onMapTapped = { lat, lon ->
                    pinnedLat = lat
                    pinnedLon = lon
                    searchError = null
                    updateReverseGeocode(lat, lon)
                },
                onCenterGpsClicked = {
                    if (currentGps != null) {
                        pinnedLat = currentGps.first
                        pinnedLon = currentGps.second
                        searchError = null
                        updateReverseGeocode(currentGps.first, currentGps.second)
                    }
                },
                floatingControlsBottomPadding = 185.dp,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Top Search & Header Bar (Wonderflow Wanda Glassmorphism)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(WandaCardElevated.copy(alpha = 0.95f))
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Selecionar: $title",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Digite o endereço ou toque no mapa",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(WandaCard)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                // Address Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        searchError = null
                    },
                    placeholder = {
                        Text("Buscar rua, avenida, bairro... (ex: Av. Paulista)", fontSize = 13.sp, color = TextMuted)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = WandaInteractive, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = WandaInteractive
                            )
                        } else if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = WandaCard,
                        unfocusedContainerColor = WandaCard,
                        focusedBorderColor = WandaInteractive,
                        unfocusedBorderColor = WandaBorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Action Bar: GPS & Search Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { performSearch() },
                        enabled = searchQuery.isNotBlank() && !isSearching,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WandaInteractive)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buscar no Mapa", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (currentGps != null) {
                        OutlinedButton(
                            onClick = {
                                pinnedLat = currentGps.first
                                pinnedLon = currentGps.second
                                searchError = null
                                updateReverseGeocode(currentGps.first, currentGps.second)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = WandaCard)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = WandaCyan20)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Meu GPS", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Error message if address not found
                if (searchError != null) {
                    Text(
                        text = searchError ?: "",
                        color = WandaRed30,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 3. Bottom Confirmation Panel
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(14.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(WandaCardElevated.copy(alpha = 0.96f))
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WandaInteractive.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = WandaInteractive, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolvedAddress ?: "Local Selecionado",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Text(
                            text = String.format(Locale.US, "Lat: %.5f • Lon: %.5f", pinnedLat, pinnedLon),
                            color = WandaCyan20,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = {
                        onConfirm(pinnedLat, pinnedLon)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WandaInteractive)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirmar Local de $title",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
