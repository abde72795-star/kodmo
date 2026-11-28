package eu.kanade.tachiyomi.ui.browse.source.trust

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

// KMK -->
class SourceTrustScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { SourceTrustScreenModel() }
        val state by screenModel.state.collectAsState()
        var menuOpen by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    titleContent = { Text(stringResource(KMR.strings.source_trust_title)) },
                    searchQuery = state.query,
                    onChangeSearchQuery = screenModel::setQuery,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.source_trust_select_10)) },
                                onClick = { menuOpen = false; screenModel.selectFirst(10) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.source_trust_select_30)) },
                                onClick = { menuOpen = false; screenModel.selectFirst(30) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.source_trust_select_all)) },
                                onClick = { menuOpen = false; screenModel.selectAll() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.source_trust_clear_selection)) },
                                onClick = { menuOpen = false; screenModel.clearSelection() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(KMR.strings.source_trust_reset)) },
                                onClick = { menuOpen = false; screenModel.resetToDefaults() },
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            if (state.loading) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }
            Column(modifier = Modifier.padding(contentPadding)) {
                Text(
                    text = stringResource(KMR.strings.source_trust_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )
                Text(
                    text = stringResource(
                        KMR.strings.source_trust_counts,
                        state.trustedCount,
                        state.rows.size,
                        state.selection.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(MaterialTheme.padding.medium),
                )
                Row(
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    Button(onClick = screenModel::trustSelected, enabled = state.selection.isNotEmpty()) {
                        Text(stringResource(KMR.strings.source_trust_trust_selected))
                    }
                    OutlinedButton(onClick = screenModel::untrustSelected, enabled = state.selection.isNotEmpty()) {
                        Text(stringResource(KMR.strings.source_trust_untrust_selected))
                    }
                }
                LazyColumn {
                    items(state.filtered, key = { it.id }) { row ->
                        val trusted = SourceTrust.isTrusted(row.id, state.untrusted)
                        val excluded = row.id.toString() in state.excluded
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { screenModel.toggleSelect(row.id) }
                                .padding(horizontal = MaterialTheme.padding.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = row.id in state.selection,
                                onCheckedChange = { screenModel.toggleSelect(row.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.name + (row.extension?.takeIf { it != row.name }?.let { " ($it)" } ?: ""),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = LocaleHelper.getLocalizedDisplayName(row.lang) + " · " +
                                        stringResource(
                                            if (trusted) KMR.strings.source_trust_state_trusted else KMR.strings.source_trust_state_untrusted,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (trusted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                            IconButton(onClick = { screenModel.toggleExcluded(row.id) }) {
                                Icon(
                                    imageVector = if (excluded) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                    contentDescription = stringResource(KMR.strings.source_trust_exclude),
                                )
                            }
                            Switch(checked = trusted, onCheckedChange = { screenModel.toggleTrust(row.id) })
                        }
                    }
                }
            }
        }
    }
}
// KMK <--
