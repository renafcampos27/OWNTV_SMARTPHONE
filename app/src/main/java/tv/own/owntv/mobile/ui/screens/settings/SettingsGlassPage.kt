package tv.own.owntv.mobile.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.core.theme.GlassPreset
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.SquircleShape
import tv.own.owntv.mobile.ui.theme.glassSurface
import tv.own.owntv.mobile.ui.theme.supportsBackdropBlur
import java.io.File

/**
 * The Glass Effect, the whole of it: the material, which parts of the app wear it, and the fine
 * tuning underneath. Every control changes the app the instant it is touched, and the panel at the
 * top is drawn with the live settings, so the effect of a slider is visible without leaving the page.
 */
@Composable
fun SettingsGlassPage(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val glass = vm.settings.glassConfig.pref(GlassConfig())
    val bgPath = vm.settings.bgImagePath.pref("")
    val context = LocalContext.current

    val alphaPct = (glass.alpha * 100).toInt()
    val blurPct = (glass.blurStrength * 100).toInt()

    // SAF, not a file browser: a phone has a picker, and the copy we keep survives the user later
    // deleting the original out of their gallery.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            vm.edit {
                val path = withContext(Dispatchers.IO) {
                    ingestBackgroundImage(context, uri)
                }
                if (path != null) setBgImagePath(path)
            }
        }
    }

    SettingsPage(modifier) {
        settingsNote(R.string.settings_glass_screen_description)
        item(key = "preview") { GlassPreview() }

        settingsGroup(key = "master") {
            SettingRow(
                title = stringResource(R.string.settings_glass_effect_title),
                subtitle = stringResource(R.string.settings_glass_master_description),
                checked = glass.enabled,
                // Off is an empty scope, so turning it back on has to put something in it: every
                // surface, which the switches further down then narrow.
                onCheckedChange = { on ->
                    val bits =
                        if (on) GlassConfig(scope = GlassSurface.entries.toSet()).toBitmask() else 0
                    vm.edit { setGlassScopeBitmask(bits) }
                },
            )
        }

        if (!glass.enabled) return@SettingsPage

        settingsSection(R.string.settings_glass_section_appearance)
        settingsNote(R.string.settings_glass_preset_title)
        settingsGroup(key = "appearance") {
            GlassPreset.entries.forEach { preset ->
                if (preset == GlassPreset.CUSTOM) return@forEach
                SettingRow(
                    title = stringResource(preset.labelRes()),
                    subtitle = stringResource(preset.descriptionRes()),
                    checked = glass.preset == preset,
                    onCheckedChange = { vm.edit { setGlassPreset(preset) } },
                )
            }
            if (glass.preset == GlassPreset.CUSTOM) {
                SettingRow(
                    title = stringResource(R.string.settings_glass_preset_custom),
                    subtitle = stringResource(R.string.settings_glass_preset_custom_description),
                    checked = true,
                    onCheckedChange = { },
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_glass_background_image),
                subtitle = if (bgPath.isBlank()) {
                    stringResource(R.string.settings_glass_background_action_description)
                } else {
                    File(bgPath).name
                },
                showChevron = true,
                onClick = { pickImage.launch(arrayOf("image/*")) },
            )
            if (bgPath.isNotBlank()) {
                SettingRow(
                    title = stringResource(R.string.common_clear),
                    onClick = { vm.edit { setBgImagePath("") } },
                )
            }
        }

        settingsSection(R.string.settings_glass_surfaces)
        settingsNote(R.string.settings_glass_surfaces_description)
        settingsGroup(key = "surfaces") {
            SettingRow(
                title = stringResource(R.string.settings_glass_surface_all),
                // "3 of 7" is the one thing a list of seven switches cannot say about itself.
                subtitle = if (glass.scope.size == GlassSurface.entries.size) {
                    stringResource(R.string.settings_surface_count_all)
                } else {
                    pluralStringResource(
                        R.plurals.settings_surface_count,
                        glass.scope.size,
                        glass.scope.size,
                        GlassSurface.entries.size,
                    )
                },
                checked = glass.scope.size == GlassSurface.entries.size,
                onCheckedChange = { on ->
                    val scope = if (on) GlassSurface.entries.toSet() else emptySet()
                    vm.edit { setGlassScopeBitmask(GlassConfig(scope = scope).toBitmask()) }
                },
            )
            GlassSurface.entries.forEach { surface ->
                SettingRow(
                    title = stringResource(surface.labelRes()),
                    checked = surface in glass.scope,
                    onCheckedChange = { on ->
                        val scope = if (on) glass.scope + surface else glass.scope - surface
                        vm.edit { setGlassScopeBitmask(GlassConfig(scope = scope).toBitmask()) }
                    },
                )
            }
        }

        settingsSection(R.string.settings_glass_section_fine_tuning) {
            SettingsSlider(
                title = stringResource(R.string.settings_transparency_title),
                subtitle = stringResource(R.string.settings_glass_transparency_short_description),
                // The slider asks "how transparent", the stored value says "how solid".
                value = 100 - alphaPct,
                range = 0..100,
                onValueChange = { pct ->
                    vm.edit { setGlassAlphaPercent(100 - pct, blurPct) }
                },
            )
            SettingsSlider(
                title = stringResource(R.string.settings_glass_background_blur_title),
                subtitle = stringResource(
                    if (supportsBackdropBlur) R.string.settings_glass_blur_short_description
                    else R.string.settings_blur_description_disabled,
                ),
                value = blurPct,
                range = 0..100,
                onValueChange = { pct -> vm.edit { setGlassBlurPercent(pct, alphaPct) } },
            )
            SettingsSlider(
                title = stringResource(R.string.settings_glass_highlight_title),
                subtitle = stringResource(R.string.settings_glass_highlight_short_description),
                value = (glass.highlightStrength * 100).toInt(),
                range = 0..100,
                onValueChange = { pct -> vm.edit { setGlassHighlightPercent(pct) } },
            )
        }

        settingsSection(R.string.settings_glass_section_behavior) {
            SettingRow(
                title = stringResource(R.string.settings_glass_depth_effects_short),
                subtitle = stringResource(R.string.settings_glass_depth_effects_short_description),
                checked = glass.depthEffects,
                onCheckedChange = { vm.edit { setGlassDepthEffects(it) } },
            )
            SettingRow(
                title = stringResource(R.string.settings_glass_shine_short),
                subtitle = stringResource(R.string.settings_glass_shine_short_description),
                checked = glass.glint,
                onCheckedChange = { vm.edit { setGlassGlint(it) } },
            )
            SettingRow(
                title = stringResource(R.string.settings_glass_full_transparency_short),
                subtitle = stringResource(
                    R.string.settings_glass_full_transparency_short_description,
                ),
                checked = glass.allowFullTransparency,
                onCheckedChange = { vm.edit { setGlassAllowFullTransparency(it) } },
            )
            SettingRow(
                title = stringResource(R.string.settings_glass_reset_balanced),
                onClick = {
                    vm.edit {
                        setGlassPreset(GlassPreset.BALANCED)
                        setGlassHighlightPercent(
                            (GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH * 100).toInt(),
                        )
                        setGlassAllowFullTransparency(false)
                        setGlassDepthEffects(true)
                        setGlassGlint(true)
                    }
                },
            )
        }
    }
}

/** A pane of the real thing, drawn with the live settings. */
@Composable
private fun GlassPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapSmall)
            .defaultMinSize(minHeight = MobileDimens.TouchTarget * 2)
            .glassSurface(GlassSurface.PANELS, SquircleShape(MobileDimens.SheetCorner)),
    ) {
        Text(
            text = stringResource(R.string.settings_glass_live_preview),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(MobileDimens.GapMedium),
        )
    }
}

/**
 * Copy a picked image into app-private storage and return its path, or null if the copy failed.
 *
 * The picked Uri itself is not persisted: its permission grant does not survive a reboot, and the
 * user may delete the original. The folder is wiped first so backgrounds never accumulate — it is
 * the same folder, and the same rule, that the backup container carries the wallpaper in.
 */
private fun ingestBackgroundImage(context: android.content.Context, uri: Uri): String? =
    runCatching {
        val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        // A fresh name every time, or Coil serves the previous bitmap from its path-keyed cache.
        val dest = File(dir, "background_${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        dest.absolutePath
    }.getOrNull()
