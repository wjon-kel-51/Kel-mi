package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.OnlineUser
import com.example.data.model.UiState
import com.example.data.model.UserContact
import com.example.security.NoxGlobalRelay
import com.example.ui.components.KelMiAvatar
import com.example.util.LocationTimeHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsTab(
    contactsState: UiState<List<UserContact>>,
    onlineUsersState: UiState<List<OnlineUser>>,
    onAddByHandle: (String) -> Unit,
    onConnectOnlineUser: (OnlineUser, Boolean) -> Unit,
    onOpenChat: (peerUid: String) -> Unit,
    onStartVoiceCall: (uid: String, name: String, color: String, lang: String) -> Unit,
    onStartVideoCall: (uid: String, name: String, color: String, lang: String) -> Unit,
    onShowMyQr: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenNoxDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddHandleDialog by remember { mutableStateOf(false) }
    var handleInput by remember { mutableStateOf("") }
    var selectedSegment by remember { mutableStateOf("ONLINE") } // "ONLINE" or "CONTACTS"
    val noxState by NoxGlobalRelay.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Global People",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Connect worldwide · QR · WeChat & QQ ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onShowMyQr, modifier = Modifier.testTag("contacts_my_qr_button")) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = "My QR Card")
                }
                IconButton(onClick = onOpenQrScanner, modifier = Modifier.testTag("contacts_scan_qr_button")) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan QR Code")
                }
                IconButton(
                    onClick = { showAddHandleDialog = true },
                    modifier = Modifier.testTag("add_handle_button")
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = "Add by Handle")
                }
            }
        }

        // Nox Global VPN / Cross-Continent Bridge Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (noxState.isConnected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable(onClick = onOpenNoxDialog)
                .testTag("nox_banner_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (noxState.isConnected) {
                                "Nox VPN Active · ${noxState.activeNode.continent}"
                            } else {
                                "Nox VPN Paused · Tap to Connect"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (noxState.isConnected) {
                                "${noxState.activeNode.hubLabel} · Chat across China & all continents"
                            } else {
                                "Enable Nox cross-continent encrypted routing"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = onOpenNoxDialog) {
                    Text("Switch")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search people, city, language, or interests...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSegment == "ONLINE",
                onClick = { selectedSegment = "ONLINE" },
                label = { Text("Online Worldwide") },
                modifier = Modifier.testTag("filter_online_people")
            )
            FilterChip(
                selected = selectedSegment == "CONTACTS",
                onClick = { selectedSegment = "CONTACTS" },
                label = { Text("My Contacts") },
                modifier = Modifier.testTag("filter_my_contacts")
            )
            OutlinedButton(onClick = onOpenQrScanner) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan QR")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedSegment == "ONLINE") {
                when (onlineUsersState) {
                    is UiState.Loading -> {
                        item(key = "loading_online") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                    }
                    is UiState.Error -> {
                        item(key = "err_online") {
                            Text(
                                text = onlineUsersState.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is UiState.Success -> {
                        val q = searchQuery.trim().lowercase()
                        val filtered = onlineUsersState.data.filter { person ->
                            q.isBlank() ||
                                person.displayName.lowercase().contains(q) ||
                                person.kelMiHandle.lowercase().contains(q) ||
                                person.qqNumber.contains(q) ||
                                person.locationLabel.lowercase().contains(q) ||
                                person.preferredLanguage.lowercase().contains(q) ||
                                person.interests.any { it.lowercase().contains(q) }
                        }
                        if (filtered.isEmpty()) {
                            item(key = "empty_online") {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No other online users found right now",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Invite friends worldwide with your Kel mi QR Card, or add them directly by @handle.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = onShowMyQr) { Text("Share My QR") }
                                            OutlinedButton(onClick = { showAddHandleDialog = true }) {
                                                Text("Add @handle")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(filtered, key = { it.userId }) { person ->
                                OnlinePersonCard(
                                    person = person,
                                    onConnect = { onConnectOnlineUser(person, false) },
                                    onChat = { onConnectOnlineUser(person, true) },
                                    onVoiceCall = {
                                        onConnectOnlineUser(person, false)
                                        onStartVoiceCall(person.userId, person.displayName, person.avatarColorHex, person.preferredLanguage)
                                    },
                                    onVideoCall = {
                                        onConnectOnlineUser(person, false)
                                        onStartVideoCall(person.userId, person.displayName, person.avatarColorHex, person.preferredLanguage)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                when (contactsState) {
                    is UiState.Loading -> {
                        item(key = "loading_contacts") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                    }
                    is UiState.Error -> {
                        item(key = "err_contacts") {
                            Text(
                                text = contactsState.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is UiState.Success -> {
                        val q = searchQuery.trim().lowercase()
                        val filtered = contactsState.data.filter { c ->
                            q.isBlank() ||
                                c.displayName.lowercase().contains(q) ||
                                c.kelMiHandle.lowercase().contains(q) ||
                                c.qqNumber.contains(q) ||
                                c.locationLabel.lowercase().contains(q) ||
                                c.preferredLanguage.lowercase().contains(q)
                        }
                        if (filtered.isEmpty()) {
                            item(key = "empty_contacts") {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No saved contacts yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Switch to 'Online Worldwide' above, scan a friend's QR code, or add by @handle.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filtered, key = { it.contactUid }) { contact ->
                                SavedContactCard(
                                    contact = contact,
                                    onChat = { onOpenChat(contact.contactUid) },
                                    onVoiceCall = {
                                        onStartVoiceCall(contact.contactUid, contact.displayName, contact.avatarColorHex, contact.preferredLanguage)
                                    },
                                    onVideoCall = {
                                        onStartVideoCall(contact.contactUid, contact.displayName, contact.avatarColorHex, contact.preferredLanguage)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddHandleDialog) {
        AlertDialog(
            onDismissRequest = { showAddHandleDialog = false },
            title = { Text("Add Friend by @Handle") },
            text = {
                OutlinedTextField(
                    value = handleInput,
                    onValueChange = { handleInput = it },
                    label = { Text("Kel mi @handle or kelmi:// link") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_handle_dialog_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (handleInput.isNotBlank()) {
                            onAddByHandle(handleInput)
                            handleInput = ""
                            showAddHandleDialog = false
                        }
                    },
                    modifier = Modifier.testTag("add_handle_confirm_button")
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddHandleDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlinePersonCard(
    person: OnlineUser,
    onConnect: () -> Unit,
    onChat: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    val peerClock = remember(person.timeZoneId) {
        if (person.timeZoneId.isNotBlank()) LocationTimeHelper.formatShortClock(person.timeZoneId) else ""
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    KelMiAvatar(name = person.displayName, colorHex = person.avatarColorHex, size = 48.dp)
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(if (person.onlineStatus == "ONLINE") Color(0xFF10B981) else Color(0xFFF59E0B))
                            .align(Alignment.BottomEnd)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = person.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (person.ageYears > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${person.ageYears} yrs",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "@${person.kelMiHandle} · ID ${person.qqNumber} · ${person.gender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(person.preferredLanguage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        if (person.locationLabel.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (peerClock.isNotBlank()) "${person.locationLabel} ($peerClock)" else person.locationLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (person.interests.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    person.interests.take(5).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onConnect, modifier = Modifier.weight(1f)) {
                    Text("Add Friend")
                }
                Button(onClick = onChat, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chat")
                }
                IconButton(onClick = onVoiceCall) {
                    Icon(Icons.Rounded.Call, contentDescription = "Voice call", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onVideoCall) {
                    Icon(Icons.Rounded.Videocam, contentDescription = "Video call", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedContactCard(
    contact: UserContact,
    onChat: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    val peerClock = remember(contact.timeZoneId) {
        if (contact.timeZoneId.isNotBlank()) LocationTimeHelper.formatShortClock(contact.timeZoneId) else ""
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChat)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KelMiAvatar(name = contact.displayName, colorHex = contact.avatarColorHex, size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (contact.ageYears > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${contact.ageYears}y", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (contact.verifiedSafetyNumber) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                        }
                    }
                    Text(
                        text = "@${contact.kelMiHandle} · ID ${contact.qqNumber} · ${contact.gender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(contact.preferredLanguage, style = MaterialTheme.typography.labelSmall)
                        if (peerClock.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(peerClock, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                IconButton(onClick = onVoiceCall) {
                    Icon(Icons.Rounded.Call, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onVideoCall) {
                    Icon(Icons.Rounded.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (contact.interests.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    contact.interests.take(5).forEach { tag ->
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}
