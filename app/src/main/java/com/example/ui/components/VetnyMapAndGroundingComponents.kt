package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.data.model.MapsGroundingSource
import com.example.data.model.VetnyGroundingPayload
import com.example.util.LocationTimeHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

private data class MapWorldHub(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double
)

private val worldMapHubs = listOf(
    MapWorldHub("Tokyo", "Shibuya & Ginza", 35.6762, 139.6503),
    MapWorldHub("Shanghai", "The Bund & Lujiazui", 31.2304, 121.4737),
    MapWorldHub("Dar es Salaam", "Kariakoo & Msasani", -6.7924, 39.2083),
    MapWorldHub("Nairobi", "Westlands & CBD", -1.2921, 36.8219),
    MapWorldHub("San Francisco", "Bay Area", 37.7749, -122.4194),
    MapWorldHub("London", "Westminster", 51.5074, -0.1278)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VetnyGroundingSection(
    grounding: VetnyGroundingPayload,
    onOpenMapExplorer: (String?) -> Unit,
    onShowBanner: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (grounding.webSources.isNotEmpty()) {
            Text(
                text = "Google Search Sources",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                grounding.webSources.forEach { src ->
                    AssistChip(
                        onClick = { openExternalUri(context, src.uri, onShowBanner) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = {
                            Text(
                                text = src.title.ifBlank { src.domain }.take(34),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }

        if (!grounding.searchEntryPointHtml.isNullOrBlank()) {
            val html = grounding.searchEntryPointHtml
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = false
                            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (grounding.mapsSources.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Google Maps",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AssistChip(
                    onClick = { onOpenMapExplorer(grounding.mapsSources.firstOrNull()?.title) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = { Text("Open Map", style = MaterialTheme.typography.labelSmall) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                grounding.mapsSources.forEach { place ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = place.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Google Maps",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (place.uri.isNotBlank()) {
                                IconButton(
                                    onClick = { openExternalUri(context, place.uri, onShowBanner) }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = "Open in Google Maps",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetnyGoogleMapDialog(
    initialQuery: String?,
    currentCoordinates: Pair<Double, Double>?,
    currentLocationLabel: String?,
    recentMapPlaces: List<MapsGroundingSource>,
    onCoordinatesUpdated: (Double, Double) -> Unit,
    onAskVetnyMaps: (String) -> Unit,
    onShowBanner: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasLocPerm by remember { mutableStateOf(LocationTimeHelper.hasLocationPermission(context)) }
    var selectedHub by remember {
        mutableStateOf(
            if (currentCoordinates != null) {
                MapWorldHub(
                    name = currentLocationLabel?.ifBlank { "My Location" } ?: "My Location",
                    subtitle = "Live GPS",
                    lat = currentCoordinates.first,
                    lng = currentCoordinates.second
                )
            } else {
                worldMapHubs.first()
            }
        )
    }

    val defaultLatLng = LatLng(selectedHub.lat, selectedHub.lng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 12f)
    }

    LaunchedEffect(selectedHub) {
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(selectedHub.lat, selectedHub.lng), 12.5f)
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocPerm = granted
        if (granted) {
            scope.launch {
                val coords = LocationTimeHelper.fetchCurrentCoordinates(context)
                if (coords != null) {
                    onCoordinatesUpdated(coords.first, coords.second)
                    selectedHub = MapWorldHub("My Location", "Current GPS", coords.first, coords.second)
                }
            }
        }
    }

    val mapsKeyConfigured = remember {
        val k = BuildConfig.MAPS_API_KEY
        k.isNotBlank() && k != "YOUR_GOOGLE_MAPS_API_KEY"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_google_map_button")) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Map")
                        }
                    },
                    title = {
                        Column {
                            Text("Google Maps Explorer", fontWeight = FontWeight.Bold)
                            Text(
                                text = initialQuery?.takeIf { it.isNotBlank() }
                                    ?: "${selectedHub.name} · ${selectedHub.subtitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (hasLocPerm) {
                                    scope.launch {
                                        val coords = LocationTimeHelper.fetchCurrentCoordinates(context)
                                        if (coords != null) {
                                            onCoordinatesUpdated(coords.first, coords.second)
                                            selectedHub = MapWorldHub(
                                                name = currentLocationLabel?.ifBlank { "My Location" } ?: "My Location",
                                                subtitle = "Live GPS",
                                                lat = coords.first,
                                                lng = coords.second
                                            )
                                        } else {
                                            onShowBanner("Could not get GPS fix yet")
                                        }
                                    }
                                } else {
                                    permLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.testTag("map_my_location_button")
                        ) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = "Use my GPS location")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!mapsKeyConfigured) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Add MAPS_API_KEY in the AI Studio Secrets panel to render live Google Maps tiles. Vetny Maps AI & Google Maps app links work right away.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // World hub quick switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    worldMapHubs.forEach { hub ->
                        FilterChip(
                            selected = selectedHub.name == hub.name,
                            onClick = { selectedHub = hub },
                            leadingIcon = {
                                Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(hub.name) }
                        )
                    }
                }

                // Interactive Google Map via Maps Compose
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = hasLocPerm),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            compassEnabled = true,
                            myLocationButtonEnabled = false
                        )
                    ) {
                        val pinPos = LatLng(selectedHub.lat, selectedHub.lng)
                        Marker(
                            state = remember(selectedHub.lat, selectedHub.lng) {
                                MarkerState(position = pinPos)
                            },
                            title = selectedHub.name,
                            snippet = selectedHub.subtitle
                        )
                    }
                }

                // Actions & Recent Maps Grounding Results
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onAskVetnyMaps("Recommend top-rated restaurants, coffee shops & places to visit near ${selectedHub.name}")
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ask_vetny_maps_area_button"),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ask Vetny Nearby", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = {
                                    val query = Uri.encode(initialQuery ?: selectedHub.name)
                                    openExternalUri(
                                        context,
                                        "https://www.google.com/maps/search/?api=1&query=$query",
                                        onShowBanner
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google Maps", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        if (recentMapPlaces.isNotEmpty()) {
                            Text(
                                text = "Recent Vetny Places · Google Maps",
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recentMapPlaces.forEach { place ->
                                    AssistChip(
                                        onClick = {
                                            val uri = place.uri.ifBlank {
                                                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(place.title)}"
                                            }
                                            openExternalUri(context, uri, onShowBanner)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        label = { Text(place.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openExternalUri(
    context: Context,
    rawUri: String,
    onShowBanner: (String) -> Unit
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUri))
        context.startActivity(intent)
    } catch (_: Exception) {
        onShowBanner("Could not open link")
    }
}
