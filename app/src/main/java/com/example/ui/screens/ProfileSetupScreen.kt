package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ProfileOptions
import com.example.data.model.UserProfile
import com.example.ui.components.LanguagePickerDialog
import com.example.util.LocationTimeHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    profile: UserProfile,
    liveLocationLabel: String?,
    isEditMode: Boolean,
    onBack: (() -> Unit)?,
    onLocationFetched: (String) -> Unit,
    onSave: (
        displayName: String,
        email: String,
        phoneNumber: String,
        kelMiHandle: String,
        gender: String,
        dateOfBirth: String,
        interests: List<String>,
        locationLabel: String,
        timeZoneId: String,
        preferredLanguage: String,
        autoTranslateEnabled: Boolean
    ) -> Unit
) {
    if (isEditMode && onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var displayName by remember(profile.userId) { mutableStateOf(profile.displayName) }
    var email by remember(profile.userId) { mutableStateOf(profile.email) }
    var phoneNumber by remember(profile.userId) { mutableStateOf(profile.phoneNumber) }
    var handle by remember(profile.userId) { mutableStateOf(profile.kelMiHandle) }
    var gender by remember(profile.userId) { mutableStateOf(profile.gender.ifBlank { "Prefer not to say" }) }
    var dateOfBirth by remember(profile.userId) { mutableStateOf(profile.dateOfBirth) }
    var selectedInterests by remember(profile.userId) { mutableStateOf(profile.interests) }
    var customInterest by remember { mutableStateOf("") }
    var locationLabel by remember(profile.userId) {
        mutableStateOf(liveLocationLabel ?: profile.locationLabel.ifBlank { "Global" })
    }
    var preferredLanguage by remember(profile.userId) {
        mutableStateOf(profile.preferredLanguage.ifBlank { "English" })
    }
    var autoTranslate by remember(profile.userId) { mutableStateOf(profile.autoTranslateEnabled) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val timeZoneId = remember { LocationTimeHelper.getCurrentTimeZoneId() }
    val localTimeText = remember { LocationTimeHelper.formatLocalTime(timeZoneId) }
    val computedAge = remember(dateOfBirth) { LocationTimeHelper.calculateAgeFromDob(dateOfBirth) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        if (granted) {
            scope.launch {
                val loc = LocationTimeHelper.fetchCurrentLocationLabel(context)
                if (!loc.isNullOrBlank()) {
                    locationLabel = loc
                    onLocationFetched(loc)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (LocationTimeHelper.hasLocationPermission(context)) {
            val loc = LocationTimeHelper.fetchCurrentLocationLabel(context)
            if (!loc.isNullOrBlank()) {
                locationLabel = loc
                onLocationFetched(loc)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Edit Kel mi Profile" else "Complete Your Kel mi Profile",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (isEditMode && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Kel mi ID (QQ): ${profile.qqNumber}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Local Time: $localTimeText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )

                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it.lowercase().replace(Regex("[^a-z0-9_]"), "") },
                    label = { Text("Kel mi Handle (@username)") },
                    prefix = { Text("@") },
                    supportingText = { Text("3-32 lowercase letters, numbers, or _") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_handle_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address (kept private)") },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_email_input")
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number (kept private)") },
                    placeholder = { Text("+86 138... / +1 555...") },
                    leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_phone_input")
                )

                // Date of Birth -> Auto Age Recognition
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = dateOfBirth,
                        onValueChange = { dateOfBirth = it },
                        label = { Text("Date of Birth (YYYY-MM-DD)") },
                        placeholder = { Text("1998-05-14") },
                        leadingIcon = { Icon(Icons.Rounded.Cake, contentDescription = null) },
                        supportingText = {
                            Text(
                                if (computedAge > 0) "Recognized Age: $computedAge years old"
                                else "Tap Pick or type YYYY-MM-DD so contacts see your age"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("profile_dob_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text("Pick")
                    }
                }

                // Gender Selection
                Column {
                    Text(
                        text = "Gender",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProfileOptions.genders.forEach { option ->
                            FilterChip(
                                selected = gender == option,
                                onClick = { gender = option },
                                label = { Text(option) },
                                leadingIcon = if (gender == option) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }

                // Likes / Interests
                Column {
                    Text(
                        text = "What You Like (Interests · up to 12)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (ProfileOptions.suggestedInterests + selectedInterests).distinct().forEach { tag ->
                            val selected = tag in selectedInterests
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedInterests = if (selected) {
                                        selectedInterests - tag
                                    } else if (selectedInterests.size < 12) {
                                        selectedInterests + tag
                                    } else selectedInterests
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customInterest,
                            onValueChange = { customInterest = it },
                            label = { Text("Add custom like / hobby") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val clean = customInterest.trim()
                                if (clean.isNotEmpty() && selectedInterests.size < 12 && clean !in selectedInterests) {
                                    selectedInterests = selectedInterests + clean
                                    customInterest = ""
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add interest", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }

                // Location & Time Access
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = locationLabel,
                        onValueChange = { locationLabel = it },
                        label = { Text("Location (City / Region)") },
                        leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (LocationTimeHelper.hasLocationPermission(context)) {
                                scope.launch {
                                    LocationTimeHelper.fetchCurrentLocationLabel(context)?.let {
                                        locationLabel = it
                                        onLocationFetched(it)
                                    }
                                }
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Detect GPS")
                    }
                }

                // Preferred Language + Vetny Auto-Translate
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguagePicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Preferred Language (All World Languages)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = preferredLanguage,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        TextButton(onClick = { showLanguagePicker = true }) {
                            Text("Change")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vetny Real-Time Auto-Translate",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Automatically translate incoming decrypted messages into $preferredLanguage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoTranslate, onCheckedChange = { autoTranslate = it })
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        when {
                            displayName.isBlank() -> validationError = "Please enter your display name."
                            handle.length < 3 -> validationError = "Handle must be at least 3 characters."
                            email.isBlank() -> validationError = "Please enter your email address."
                            phoneNumber.isBlank() -> validationError = "Please enter your phone number."
                            dateOfBirth.isNotBlank() && computedAge <= 0 ->
                                validationError = "Enter Date of Birth as YYYY-MM-DD (e.g. 1998-05-14)."
                            else -> {
                                validationError = null
                                onSave(
                                    displayName,
                                    email,
                                    phoneNumber,
                                    handle,
                                    gender,
                                    dateOfBirth,
                                    selectedInterests,
                                    locationLabel,
                                    timeZoneId,
                                    preferredLanguage,
                                    autoTranslate
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_profile_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (isEditMode) "Save Profile Changes" else "Enter Kel mi",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            dateOfBirth = fmt.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            title = "Choose Your Language",
            currentLanguage = preferredLanguage,
            onSelectLanguage = { preferredLanguage = it },
            onDismiss = { showLanguagePicker = false }
        )
    }
}
