package it.vfsfitvnm.vimusic.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.credentials.CredentialManager
import it.vfsfitvnm.compose.persist.persistList
import it.vfsfitvnm.core.ui.LocalAppearance
import it.vfsfitvnm.providers.piped.Piped
import it.vfsfitvnm.providers.piped.models.Instance
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalCredentialManager
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.features.spotify.ImportStatus
import it.vfsfitvnm.vimusic.features.spotify.Spotify
import it.vfsfitvnm.vimusic.features.spotify.SpotifyPlaylistProcessor
import it.vfsfitvnm.vimusic.models.PipedSession
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.vimusic.ui.components.themed.*
import it.vfsfitvnm.vimusic.ui.screens.Route
import it.vfsfitvnm.vimusic.utils.*
import io.ktor.http.Url
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Route
@Composable
fun SyncSettings(
    credentialManager: CredentialManager = LocalCredentialManager.current
) {
    val coroutineScope = rememberCoroutineScope()
    val (colorPalette, typography) = LocalAppearance.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val pipedSessions by Database.instance.pipedSessions().collectAsState(initial = listOf())

    var linkingPiped by remember { mutableStateOf(false) }
    var showingSpotifyIdDialog by remember { mutableStateOf(false) }
    var showingNameDialogForJson by remember { mutableStateOf<String?>(null) }
    var deletingPipedSession: Int? by rememberSaveable { mutableStateOf(null) }

    // --- 1. NEW STATE: This will hold the info and trigger our progress dialog ---
    var importInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Dialog 1: Ask for Spotify Playlist ID
    if (showingSpotifyIdDialog) {
        DefaultDialog(onDismiss = { showingSpotifyIdDialog = false }) {
            var playlistId by rememberSaveable { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxWidth().padding(all = 24.dp)) {
                BasicText(
                    text = stringResource(R.string.import_spotify_playlist_title),
                    style = typography.m.semiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                TextField(
                    value = playlistId, onValueChange = { playlistId = it },
                    hintText = stringResource(R.string.playlist_id_hint),
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        DialogTextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { showingSpotifyIdDialog = false },
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                        DialogTextButton(
                            text = stringResource(R.string.import_text),
                            enabled = playlistId.isNotBlank(),
                            onClick = {
                                isLoading = true
                                coroutineScope.launch {
                                    val rawJson = Spotify().getPlaylist(playlistId)
                                    if (rawJson != null) {
                                        showingSpotifyIdDialog = false
                                        showingNameDialogForJson = rawJson
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Failed to fetch playlist", Toast.LENGTH_SHORT).show()
                                        }
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }

    // Dialog 2: Ask for the new playlist's name
    showingNameDialogForJson?.let { rawJson ->
        DefaultDialog(onDismiss = { showingNameDialogForJson = null }) {
            var playlistName by rememberSaveable { mutableStateOf("") }

            Column(modifier = Modifier.fillMaxWidth().padding(all = 24.dp)) {
                BasicText(
                    text = stringResource(R.string.name_your_playlist_title),
                    style = typography.m.semiBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                TextField(
                    value = playlistName, onValueChange = { playlistName = it },
                    hintText = stringResource(R.string.playlist_name_hint)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    DialogTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showingNameDialogForJson = null },
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    DialogTextButton(
                        text = stringResource(R.string.create_playlist_button),
                        enabled = playlistName.isNotBlank(),
                        onClick = {
                            // --- 2. MODIFIED ONCLICK: This now triggers the progress dialog ---
                            importInfo = rawJson to playlistName
                            showingNameDialogForJson = null
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }

    // --- 3. NEW DIALOG: This is the progress dialog that appears when you click "Create Playlist" ---
    importInfo?.let { (rawJson, playlistName) ->
        var importStatus by remember { mutableStateOf<ImportStatus>(ImportStatus.Idle) }
        val scope = rememberCoroutineScope()

        // This automatically starts the import process when the dialog is shown
        LaunchedEffect(Unit) {
            scope.launch {
                val processor = SpotifyPlaylistProcessor()
                processor.processAndImportPlaylist(rawJson, playlistName) { statusUpdate ->
                    importStatus = statusUpdate
                }
            }
        }

        DefaultDialog(
            onDismiss = {
                // Only allow dismissing if the import is not running
                if (importStatus !is ImportStatus.InProgress) {
                    importInfo = null
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(all = 24.dp)) {
                val title = when (importStatus) {
                    is ImportStatus.InProgress -> "Importing Playlist..."
                    is ImportStatus.Complete -> "Import Complete"
                    is ImportStatus.Error -> "Import Failed"
                    is ImportStatus.Idle -> "Starting..."
                }
                Text(text = title, style = typography.m.semiBold)
                Spacer(modifier = Modifier.height(16.dp))

                when (val status = importStatus) {
                    is ImportStatus.InProgress -> {
                        val progress by animateFloatAsState(
                            targetValue = if (status.total > 0) status.processed.toFloat() / status.total else 0f,
                            label = "ImportProgress"
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Processed ${status.processed} of ${status.total} songs",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is ImportStatus.Complete -> {
                        Text("Successfully imported ${status.imported} of ${status.total} songs.")
                        if (status.failed > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Failed to find ${status.failed} songs.")
                        }
                    }
                    is ImportStatus.Error -> {
                        Text("An error occurred: ${status.message}", color = MaterialTheme.colorScheme.error)
                    }
                    is ImportStatus.Idle -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Initializing import process...")
                    }
                }

                if (importStatus !is ImportStatus.InProgress) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        DialogTextButton(
                            text = "OK",
                            onClick = { importInfo = null },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }

    if (linkingPiped) DefaultDialog(
        onDismiss = { linkingPiped = false },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var isLoading by rememberSaveable { mutableStateOf(false) }
        var hasError by rememberSaveable { mutableStateOf(false) }
        var successful by remember { mutableStateOf(false) }

        when {
            successful -> BasicText(
                text = stringResource(R.string.piped_session_created_successfully),
                style = typography.xs.semiBold.center,
                modifier = Modifier.padding(all = 24.dp)
            )

            hasError -> ConfirmationDialogBody(
                text = stringResource(R.string.error_piped_link),
                onDismiss = { },
                onCancel = { linkingPiped = false },
                onConfirm = { hasError = false }
            )

            isLoading -> CircularProgressIndicator(modifier = Modifier.padding(all = 8.dp))

            else -> Box(modifier = Modifier.fillMaxWidth()) {
                var backgroundLoading by rememberSaveable { mutableStateOf(false) }
                if (backgroundLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.TopEnd))

                Column(modifier = Modifier.fillMaxWidth()) {
                    var instances by persistList<Instance>(tag = "settings/sync/piped/instances")
                    var loadingInstances by rememberSaveable { mutableStateOf(true) }
                    var selectedInstance: Int? by rememberSaveable { mutableStateOf(null) }
                    var username by rememberSaveable { mutableStateOf("") }
                    var password by rememberSaveable { mutableStateOf("") }
                    var canSelect by rememberSaveable { mutableStateOf(false) }
                    var instancesUnavailable by rememberSaveable { mutableStateOf(false) }
                    var customInstance: String? by rememberSaveable { mutableStateOf(null) }

                    LaunchedEffect(Unit) {
                        Piped.getInstances()?.getOrNull()?.let {
                            selectedInstance = null
                            instances = it.toImmutableList()
                            canSelect = true
                        } ?: run { instancesUnavailable = true }
                        loadingInstances = false

                        backgroundLoading = true
                        runCatching {
                            credentialManager.get(context)?.let {
                                username = it.id
                                password = it.password
                            }
                        }.getOrNull()
                        backgroundLoading = false
                    }

                    BasicText(
                        text = stringResource(R.string.piped),
                        style = typography.m.semiBold
                    )

                    if (customInstance == null) ValueSelectorSettingsEntry(
                        title = stringResource(R.string.instance),
                        selectedValue = selectedInstance,
                        values = instances.indices.toImmutableList(),
                        onValueSelect = { selectedInstance = it },
                        valueText = { idx ->
                            idx?.let { instances.getOrNull(it)?.name }
                                ?: if (instancesUnavailable) stringResource(R.string.error_piped_instances_unavailable)
                                else stringResource(R.string.click_to_select)
                        },
                        isEnabled = !instancesUnavailable && canSelect,
                        usePadding = false,
                        trailingContent = if (loadingInstances) {
                            { CircularProgressIndicator() }
                        } else null
                    )
                    SwitchSettingsEntry(
                        title = stringResource(R.string.custom_instance),
                        text = null,
                        isChecked = customInstance != null,
                        onCheckedChange = {
                            customInstance = if (customInstance == null) "" else null
                        },
                        usePadding = false
                    )
                    customInstance?.let { instance ->
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = instance,
                            onValueChange = { customInstance = it },
                            hintText = stringResource(R.string.base_api_url),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = username,
                        onValueChange = { username = it },
                        hintText = stringResource(R.string.username),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        hintText = stringResource(R.string.password),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                password()
                            }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DialogTextButton(
                        text = stringResource(R.string.login),
                        primary = true,
                        enabled = (customInstance?.isNotBlank() == true || selectedInstance != null) &&
                            username.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            @Suppress("Wrapping") // thank you ktlint
                            (customInstance?.let {
                                runCatching {
                                    Url(it)
                                }.getOrNull() ?: runCatching {
                                    Url("https://$it")
                                }.getOrNull()
                            } ?: selectedInstance?.let { instances[it].apiBaseUrl })?.let { url ->
                                coroutineScope.launch {
                                    isLoading = true
                                    val session = Piped.login(
                                        apiBaseUrl = url,
                                        username = username,
                                        password = password
                                    )?.getOrNull()
                                    isLoading = false
                                    if (session == null) {
                                        hasError = true
                                        return@launch
                                    }

                                    transaction {
                                        Database.instance.insert(
                                            PipedSession(
                                                apiBaseUrl = session.apiBaseUrl,
                                                username = username,
                                                token = session.token
                                            )
                                        )
                                    }

                                    successful = true

                                    runCatching {
                                        credentialManager.upsert(
                                            context = context,
                                            username = username,
                                            password = password
                                        )
                                    }

                                    linkingPiped = false
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    if (deletingPipedSession != null) ConfirmationDialog(
        text = stringResource(R.string.confirm_delete_piped_session),
        onDismiss = {
            deletingPipedSession = null
        },
        onConfirm = {
            deletingPipedSession?.let {
                transaction { Database.instance.delete(pipedSessions[it]) }
            }
        }
    )

    SettingsCategoryScreen(title = stringResource(R.string.sync)) {
        SettingsDescription(text = stringResource(R.string.sync_description))
        SettingsGroup(title = stringResource(R.string.spotify)) {
            SettingsEntry(
                title = stringResource(R.string.import_from_spotify),
                text = stringResource(R.string.import_from_spotify_description),
                onClick = { showingSpotifyIdDialog = true }
            )
        }
        SettingsGroup(title = stringResource(R.string.piped)) {
            SettingsEntry(
                title = stringResource(R.string.add_account),
                text = stringResource(R.string.add_account_description),
                onClick = { linkingPiped = true }
            )
            SettingsEntry(
                title = stringResource(R.string.learn_more),
                text = stringResource(R.string.learn_more_description),
                onClick = { uriHandler.openUri("https://github.com/TeamPiped/Piped/blob/master/README.md") }
            )
        }
        SettingsGroup(title = stringResource(R.string.piped_sessions)) {
            if (pipedSessions.isEmpty()) {
                SettingsGroupSpacer()
                BasicText(
                    text = stringResource(R.string.no_items_found),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = typography.s.semiBold.center
                )
            } else pipedSessions.fastForEachIndexed { i, session ->
                SettingsEntry(
                    title = session.username,
                    text = session.apiBaseUrl.toString(),
                    onClick = { },
                    trailingContent = {
                        IconButton(
                            onClick = { deletingPipedSession = i },
                            icon = R.drawable.delete,
                            color = colorPalette.text
                        )
                    }
                )
            }
        }
    }
}
