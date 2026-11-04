package dev.nanobot.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nanobot.protocol.WorkspaceDirectoriesResponse
import dev.nanobot.protocol.WorkspaceProject

/**
 * The WebUI's workspace picker, reduced to a phone sheet: saved folders first, then a directory
 * browser that starts at the gateway's default project.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddWorkspaceSheet(
    hostName: String,
    defaultPath: String,
    canBrowse: Boolean,
    favoriteProjects: List<WorkspaceProject>,
    recentProjects: List<WorkspaceProject>,
    browse: suspend (String) -> WorkspaceDirectoriesResponse,
    onSelect: (WorkspaceProject) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        WorkspacePickerContent(
            hostName = hostName,
            defaultPath = defaultPath,
            canBrowse = canBrowse,
            favoriteProjects = favoriteProjects,
            recentProjects = recentProjects,
            browse = browse,
            onSelect = onSelect,
            onClose = onDismiss,
        )
    }
}

@Composable
internal fun WorkspacePickerContent(
    hostName: String,
    defaultPath: String,
    canBrowse: Boolean,
    favoriteProjects: List<WorkspaceProject>,
    recentProjects: List<WorkspaceProject>,
    browse: suspend (String) -> WorkspaceDirectoriesResponse,
    onSelect: (WorkspaceProject) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var browsing by rememberSaveable { mutableStateOf(false) }
    var browsePath by rememberSaveable { mutableStateOf(defaultPath) }
    var filter by rememberSaveable { mutableStateOf("") }
    var directory by remember { mutableStateOf<WorkspaceDirectoriesResponse?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val strings = LocalStrings.current

    LaunchedEffect(browsing, browsePath) {
        if (!browsing) return@LaunchedEffect
        loading = true
        error = null
        try {
            directory = browse(browsePath)
        } catch (failure: Exception) {
            directory = null
            error = failure.message ?: strings.cannotReadFolder
        } finally {
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (browsing) {
                IconButton(onClick = {
                    browsing = false
                    filter = ""
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backToSavedWorkspaces)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(strings.addWorkspace, style = MaterialTheme.typography.titleLarge)
                if (hostName.isNotBlank()) {
                    Text(
                        hostName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = strings.close)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (browsing) {
            BrowsingPicker(
                browsePath = browsePath,
                filter = filter,
                onFilterChange = { filter = it },
                directory = directory,
                loading = loading,
                error = error,
                onNavigate = { path ->
                    browsePath = path
                    filter = ""
                },
                onUp = {
                    directory?.parent?.let { parent ->
                        browsePath = parent
                        filter = ""
                    }
                },
                onSelect = { path ->
                    onSelect(WorkspaceProject(projectNameFromPath(path), path))
                },
            )
        } else {
            SavedWorkspaces(
                favoriteProjects = favoriteProjects,
                recentProjects = recentProjects,
                canBrowse = canBrowse,
                onSelect = onSelect,
                onBrowse = {
                    browsePath = defaultPath
                    browsing = true
                },
            )
        }
    }
}

@Composable
private fun SavedWorkspaces(
    favoriteProjects: List<WorkspaceProject>,
    recentProjects: List<WorkspaceProject>,
    canBrowse: Boolean,
    onSelect: (WorkspaceProject) -> Unit,
    onBrowse: () -> Unit,
) {
    val strings = LocalStrings.current
    val favorites = favoriteProjects.distinctBy { it.path }
    val favoritesPaths = favorites.map { it.path }.toSet()
    val recent = recentProjects
        .filterNot { it.path in favoritesPaths }
        .distinctBy { it.path }

    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
        if (favorites.isNotEmpty()) {
            item(key = "favorites-title") { SectionTitle(strings.favorites) }
            items(favorites, key = { "favorite-${it.path}" }) { project ->
                WorkspaceRow(project = project, onClick = { onSelect(project) })
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent-title") { SectionTitle(strings.recent) }
            items(recent, key = { "recent-${it.path}" }) { project ->
                WorkspaceRow(project = project, onClick = { onSelect(project) })
            }
        }
        if (favorites.isEmpty() && recent.isEmpty()) {
            item(key = "empty") {
                Text(
                    strings.noSavedWorkspaces,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        if (canBrowse) {
            item(key = "browse") {
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = onBrowse,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(strings.browseFolders, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowsingPicker(
    browsePath: String,
    filter: String,
    onFilterChange: (String) -> Unit,
    directory: WorkspaceDirectoriesResponse?,
    loading: Boolean,
    error: String?,
    onNavigate: (String) -> Unit,
    onUp: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val currentPath = directory?.path ?: browsePath
    val entries = remember(directory, filter) {
        val needle = filter.trim()
        directory?.entries.orEmpty().filter {
            needle.isEmpty() || it.name.contains(needle, ignoreCase = true)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onUp, enabled = directory?.parent != null) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.upOneLevel)
        }
        Text(
            currentPath,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onSelect(currentPath) },
            enabled = !loading && directory != null,
        ) {
            Text(strings.add)
        }
    }
    OutlinedTextField(
        value = filter,
        onValueChange = onFilterChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(strings.filterFolders) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
    Spacer(Modifier.height(8.dp))

    when {
        loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }

        error != null -> Text(
            error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 20.dp),
        )

        entries.isEmpty() -> Text(
            if (filter.isBlank()) strings.noSubfolders else strings.noMatchingFolders,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )

        else -> LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
            items(entries, key = { it.path }) { entry ->
                FolderRow(entry = entry, onClick = { onNavigate(entry.path) })
            }
            if (directory?.truncated == true) {
                item(key = "truncated") {
                    Text(
                        strings.showingFirst500,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun WorkspaceRow(project: WorkspaceProject, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WorkspaceFolderIcon()
            Column(Modifier.weight(1f)) {
                Text(
                    project.name.ifBlank { projectNameFromPath(project.path) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    project.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FolderRow(entry: WorkspaceProject, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkspaceFolderIcon()
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun WorkspaceFolderIcon() {
    Icon(
        painterResource(R.drawable.ic_folder),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
    )
}
