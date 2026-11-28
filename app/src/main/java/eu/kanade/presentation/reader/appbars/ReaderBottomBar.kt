package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.translate.PageTranslationService
import eu.kanade.tachiyomi.ui.reader.translate.TranslationPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableSet
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ReaderBottomBar(
    // SY -->
    enabledButtons: ImmutableSet<String>,
    // SY <--
    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
    // SY -->
    currentReadingMode: ReadingMode,
    dualPageSplitEnabled: Boolean,
    doublePages: Boolean,
    onClickChapterList: () -> Unit,
    onClickWebView: (() -> Unit)?,
    onClickBrowser: (() -> Unit)?,
    onClickShare: (() -> Unit)?,
    onClickPageLayout: () -> Unit,
    onClickShiftPage: () -> Unit,
    // SY <--
    modifier: Modifier = Modifier,
) {
    // KMK -->
    val iconColor = MaterialTheme.colorScheme.primary
    // KMK <--
    Row(
        modifier = modifier
            .pointerInput(Unit) {},
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // SY -->
        if (ReaderBottomButton.ViewChapters.isIn(enabledButtons)) {
            IconButton(onClick = onClickChapterList) {
                Icon(
                    imageVector = Icons.Outlined.FormatListNumbered,
                    contentDescription = stringResource(MR.strings.chapters),
                    // KMK -->
                    tint = iconColor,
                    // KMK <--
                )
            }
        }

        if (ReaderBottomButton.WebView.isIn(enabledButtons) && onClickWebView != null) {
            IconButton(onClick = onClickWebView) {
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = stringResource(MR.strings.action_open_in_web_view),
                    // KMK -->
                    tint = iconColor,
                    // KMK <--
                )
            }
        }

        if (ReaderBottomButton.Browser.isIn(enabledButtons) && onClickBrowser != null) {
            IconButton(onClick = onClickBrowser) {
                Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = stringResource(MR.strings.action_open_in_browser),
                    // KMK -->
                    tint = iconColor,
                    // KMK <--
                )
            }
        }

        if (ReaderBottomButton.Share.isIn(enabledButtons) && onClickShare != null) {
            IconButton(onClick = onClickShare) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(MR.strings.action_share),
                    // KMK -->
                    tint = iconColor,
                    // KMK <--
                )
            }
        }

        if (ReaderBottomButton.ReadingMode.isIn(enabledButtons)) {
            IconButton(onClick = onClickReadingMode) {
                Icon(
                    painter = painterResource(readingMode.iconRes),
                    contentDescription = stringResource(MR.strings.viewer),
                    // KMK -->
                    tint = iconColor,
                    // KMK <--
                )
            }
        }

        if (ReaderBottomButton.Rotation.isIn(enabledButtons)) {
            IconButton(onClick = onClickOrientation) {
                Icon(
                    imageVector = orientation.icon,
                    contentDescription = stringResource(MR.strings.pref_rotation_type),
                    // KMK -->
                    tint = iconColor,
                    // KMK <--
                )
            }
        }

        val cropBorders = when (currentReadingMode) {
            ReadingMode.WEBTOON -> ReaderBottomButton.CropBordersWebtoon
            ReadingMode.CONTINUOUS_VERTICAL -> ReaderBottomButton.CropBordersContinuesVertical
            else -> ReaderBottomButton.CropBordersPager
        }
        if (cropBorders.isIn(enabledButtons)) {
            IconButton(onClick = onClickCropBorder) {
                Icon(
                    painter = painterResource(
                        if (cropEnabled) R.drawable.ic_crop_24dp else R.drawable.ic_crop_off_24dp,
                    ),
                    contentDescription = stringResource(MR.strings.pref_crop_borders),
                    // KMK -->
                    tint = iconColor,
                    // KMK <--
                )
            }
        }

        if (
            !dualPageSplitEnabled &&
            ReaderBottomButton.PageLayout.isIn(enabledButtons) &&
            ReadingMode.isPagerType(currentReadingMode.flagValue)
        ) {
            IconButton(onClick = onClickPageLayout) {
                Icon(
                    painter = painterResource(R.drawable.ic_book_open_variant_24dp),
                    contentDescription = stringResource(SYMR.strings.page_layout),
                    // KMK -->
                    tint = iconColor,
                    // KMK <--
                )
            }
        }

        if (doublePages) {
            IconButton(onClick = onClickShiftPage) {
                Icon(
                    painter = painterResource(R.drawable.ic_page_next_outline_24dp),
                    contentDescription = stringResource(SYMR.strings.shift_double_pages),
                    // KMK -->
                    tint = iconColor,
                    // KMK <--
                )
            }
        }

        // KMK -->
        TranslateButton(iconColor = iconColor)
        // KMK <--

        IconButton(onClick = onClickSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(MR.strings.action_settings),
                // KMK -->
                tint = iconColor,
                // KMK <--
            )
        }
        // SY <--
    }
}

// KMK -->
/** Switches page translation on or off and shows what the engine is doing. */
@Composable
private fun TranslateButton(iconColor: Color) {
    val context = LocalContext.current
    val preferences = remember { Injekt.get<TranslationPreferences>() }
    val enabled by preferences.enabled().collectAsState()
    val status by PageTranslationService.status.collectAsState()

    if (enabled) {
        val current = status
        if (current is PageTranslationService.Status.Failed) {
            val message = stringResource(KMR.strings.page_translation_failed, current.message)
            LaunchedEffect(current) { context.toast(message) }
        }
    }

    IconButton(onClick = { preferences.enabled().set(!enabled) }) {
        val current = status
        when {
            enabled && current is PageTranslationService.Status.Working -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = iconColor,
            )
            else -> Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = stringResource(
                    if (enabled) KMR.strings.action_show_original_page else KMR.strings.action_translate_page,
                ),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    current is PageTranslationService.Status.Failed -> MaterialTheme.colorScheme.error
                    else -> iconColor
                },
            )
        }
    }
}
// KMK <--
