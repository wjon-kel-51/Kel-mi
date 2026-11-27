package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.KelMiDatabase
import com.example.data.remote.KelMiRepository
import com.example.data.remote.KelMiSocialRepository
import com.example.data.remote.VetnyAiService
import com.example.security.SignalProtocolManager
import com.example.security.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.screens.KelMiAppScaffold
import com.example.ui.theme.KelMiTheme
import com.example.ui.viewmodel.KelMiViewModel
import com.example.util.DevicePermissionsHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        DevicePermissionsHelper.ensureScreenshotsAllowed(this)
        enableEdgeToEdge()

        setContent {
            KelMiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val scope = rememberCoroutineScope()
                    val credentialManager = remember { CredentialManager.create(this@MainActivity) }
                    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            currentUser = firebaseAuth.currentUser
                        }
                        Firebase.auth.addAuthStateListener(listener)
                        onDispose {
                            Firebase.auth.removeAuthStateListener(listener)
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (currentUser == null) {
                            attemptAutoSignIn(
                                context = this@MainActivity,
                                credentialManager = credentialManager,
                                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                                onUnauthenticated = { },
                                scope = scope
                            )
                        }
                    }

                    val user = currentUser
                    if (user == null) {
                        AuthScreen(
                            onAuthSuccess = {
                                currentUser = Firebase.auth.currentUser
                            }
                        )
                    } else {
                        key(user.uid) {
                            val app = applicationContext
                            val vm: KelMiViewModel = viewModel(
                                key = user.uid,
                                factory = viewModelFactory {
                                    initializer {
                                        val dbId = app.getString(R.string.firestore_database_id)
                                        val firestore = FirebaseFirestore.getInstance(dbId)
                                        val localDb = KelMiDatabase.getInstance(app)
                                        val signalManager = SignalProtocolManager(app, user.uid)
                                        val repository = KelMiRepository(
                                            db = firestore,
                                            signalManager = signalManager,
                                            decryptedDao = localDb.decryptedMessageDao()
                                        )
                                        val socialRepository = KelMiSocialRepository(db = firestore)
                                        KelMiViewModel(
                                            repository = repository,
                                            socialRepository = socialRepository,
                                            vetnyAiService = VetnyAiService(),
                                            signalManager = signalManager,
                                            currentUserId = user.uid
                                        )
                                    }
                                }
                            )
                            KelMiAppScaffold(
                                viewModel = vm,
                                currentUserId = user.uid
                            )
                        }
                    }
                }
            }
        }
    }
}
