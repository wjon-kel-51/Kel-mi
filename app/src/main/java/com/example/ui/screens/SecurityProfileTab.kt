package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.model.ReelPost
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.security.NoxGlobalRelay
import com.example.security.SignalProtocolManager
import com.example.ui.auth.signOut
import com.example.ui.components.KelMiAvatar
import com.example.ui.components.LanguagePickerDialog
import com.example.data.remote.KelMiRepository
import com.example.util.DevicePermissionsHelper
import com.example.util.LocationTimeHelper
import com.example.util.MediaEncodeHelper
import com.example.util.ScreenshotHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityProfileTab(
    userProfile: UserProfile?,
    keyDiagnostics: SignalProtocolManager.LocalKeyDiagnostics,
    contactCount: Int,
    myReels: List<ReelPost>,
    savedReelsCount: Int,
    onEditProfile: () -> Unit,
    onShowMyQr: () -> Unit,
    onOpenNoxDialog: () -> Unit,
    onUpdateTranslationPrefs: (String, Boolean) -> Unit,
    onSetDiscoverableOnline: (Boolean) -> Unit,
    onRotateSignedPreKey: () -> Unit,
    onReplenishOpks: () -> Unit,
    onShowBanner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val noxState by NoxGlobalRelay.state.collectAsState()

    var showLanguagePicker by remember { mutableStateOf(false) }
    var discoverableOnline by remember { mutableStateOf(true) }
    var permRefreshTick by remember { mutableIntStateOf(0) }

    val cameraGranted = remember(permRefreshTick) { DevicePermissionsHelper.hasCameraPermission(context) }
    val micGranted = remember(permRefreshTick) { DevicePermissionsHelper.hasMicrophonePermission(context) }
    val locGranted = remember(permRefreshTick) { DevicePermissionsHelper.hasLocationPermission(context) }
    val notifGranted = remember(permRefreshTick) { DevicePermissionsHelper.hasNotificationPermission(context) }
    val screenshotsAllowed = remember(permRefreshTick) { DevicePermissionsHelper.areScreenshotsAllowed(context) }

    val singlePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permRefreshTick++
    }
    val multiPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permRefreshTick++
    }

    val profile = userProfile
    val localClock = remember(profile?.timeZoneId) {
        LocationTimeHelper.formatLocalTime(profile?.timeZoneId)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Profile Card + Instagram Stats
        item(key = "profile_header") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KelMiAvatar(
                            name = profile?.displayName ?: "Kel mi",
                            colorHex = KelMiRepository.pickAvatarColor(profile?.userId ?: "kelmi"),
                            size = 62.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile?.displayName ?: "Kel mi User",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${profile?.kelMiHandle ?: "user"} · ID ${profile?.qqNumber ?: "---"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val ageGender = buildList {
                                if ((profile?.ageYears ?: 0L) > 0L) add("${profile?.ageYears} yrs")
                                if (!profile?.gender.isNullOrBlank()) add(profile!!.gender)
                            }.joinToString(" · ")
                            if (ageGender.isNotBlank()) {
                                Text(
                                    text = ageGender,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Instagram-style Profile Counters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatColumn(label = "Reels", value = "${myReels.size}")
                        ProfileStatColumn(label = "Contacts", value = "$contactCount")
                        ProfileStatColumn(label = "Saved", value = "$savedReelsCount")
                    }

                    if (!profile?.email.isNullOrBlank() || !profile?.phoneNumber.isNullOrBlank()) {
                        HorizontalDivider()
                        if (!profile?.email.isNullOrBlank()) {
                            Text("Email: ${profile!!.email}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!profile?.phoneNumber.isNullOrBlank()) {
                            Text("Phone: ${profile!!.phoneNumber}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!profile?.dateOfBirth.isNullOrBlank()) {
                            Text("DOB: ${profile!!.dateOfBirth} (Age ${profile.ageYears})", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(profile?.locationLabel?.ifBlank { "Global" } ?: "Global", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(localClock, style = MaterialTheme.typography.bodySmall)
                    }

                    if (!profile?.interests.isNullOrEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            profile!!.interests.forEach { tag ->
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onEditProfile,
                            modifier = Modifier.weight(1f).testTag("edit_profile_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Profile")
                        }
                        OutlinedButton(
                            onClick = onShowMyQr,
                            modifier = Modifier.weight(1f).testTag("profile_my_qr_button")
                        ) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("My QR Card")
                        }
                    }
                }
            }
        }

        // Instagram Profile Reels Grid Preview
        if (myReels.isNotEmpty()) {
            item(key = "my_reels_grid") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your Reels & Photo Grid", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            myReels.take(3).forEach { reel ->
                                val bmp = remember(reel.mediaBase64) { MediaEncodeHelper.decodeBase64Bitmap(reel.mediaBase64) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = reel.caption,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = reel.caption.take(18),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Device Permissions & Screenshots Panel
        item(key = "device_permissions") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Device Permissions & Screenshots",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    PermissionRow(
                        icon = Icons.Rounded.CameraAlt,
                        label = "Camera (Video Calls, QR & Reels)",
                        granted = cameraGranted,
                        onGrant = { singlePermLauncher.launch(Manifest.permission.CAMERA) }
                    )
                    PermissionRow(
                        icon = Icons.Rounded.Mic,
                        label = "Microphone (Voice & Video Calls)",
                        granted = micGranted,
                        onGrant = { singlePermLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                    PermissionRow(
                        icon = Icons.Rounded.LocationOn,
                        label = "Location & Time Access",
                        granted = locGranted,
                        onGrant = {
                            multiPermLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                    PermissionRow(
                        icon = Icons.Rounded.Notifications,
                        label = "Call & Message Notifications",
                        granted = notifGranted,
                        onGrant = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                singlePermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )

                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (screenshotsAllowed) "Screenshots Allowed ✓" else "Screenshots Restricted",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "System screenshots enabled everywhere · Media uses zero-permission Photo Picker",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    ScreenshotHelper.captureAndShareScreenshot(context)
                                        .onSuccess { res ->
                                            onShowBanner(
                                                if (res.savedToGallery) "Screenshot saved & ready to share"
                                                else "Screenshot ready to share"
                                            )
                                        }
                                        .onFailure { onShowBanner(it.localizedMessage ?: "Screenshot failed") }
                                }
                            },
                            modifier = Modifier.testTag("profile_screenshot_button")
                        ) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Capture")
                        }
                    }
                }
            }
        }

        // Nox Global VPN & Discoverability
        item(key = "nox_and_discovery") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenNoxDialog),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Nox Global Encrypted VPN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (noxState.isConnected) "${noxState.activeNode.continent} · ${noxState.activeNode.hubLabel}"
                                    else "Disconnected · Tap to connect across continents",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = onOpenNoxDialog) { Text("Configure") }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Appear in Online People Worldwide", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Let other verified Kel mi users connect with you globally", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = discoverableOnline,
                            onCheckedChange = {
                                discoverableOnline = it
                                onSetDiscoverableOnline(it)
                            }
                        )
                    }
                }
            }
        }

        // Vetny World Translation Preferences
        item(key = "vetny_prefs") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showLanguagePicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Vetny Preferred Language", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(profile?.preferredLanguage ?: "English", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        TextButton(onClick = { showLanguagePicker = true }) { Text("Change") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Auto-Translate Incoming Chats", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = profile?.autoTranslateEnabled ?: true,
                            onCheckedChange = { enabled ->
                                onUpdateTranslationPrefs(profile?.preferredLanguage ?: "English", enabled)
                            }
                        )
                    }
                }
            }
        }

        // Sign Out
        item(key = "sign_out") {
            OutlinedButton(
                onClick = { signOut(credentialManager = credentialManager, scope = scope) },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out of Kel mi")
            }
        }
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            title = "Select World Language",
            currentLanguage = profile?.preferredLanguage ?: "English",
            onSelectLanguage = { newLang ->
                onUpdateTranslationPrefs(newLang, profile?.autoTranslateEnabled ?: true)
            },
            onDismiss = { showLanguagePicker = false }
        )
    }
}

@Composable
private fun ProfileStatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Granted", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Allowed", style = MaterialTheme.typography.labelMedium, color = Color(0xFF10B981))
            }
        } else {
            TextButton(onClick = onGrant) { Text("Allow") }
        }
    }
}
