package tv.own.owntv.mobile.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.util.Locale

/**
 * The weather shown beside the clock: whether it appears at all, where it is measured, and in which
 * unit.
 *
 * "Where" is the part a phone can answer that a television cannot — it moves. Left to itself the
 * weather is looked up from the network address, which on a phone is whichever city the mobile
 * operator routes through; the device's own position is both more accurate and cheaper to get.
 */
@Composable
fun SettingsWeatherPage(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val enabled = vm.settings.weatherEnabled.pref(false)
    val fahrenheit = vm.settings.weatherFahrenheit.pref(false)
    val stored = vm.settings.weatherLocation.pref("")
    val context = LocalContext.current
    // A stored "52.5,13.4" is a fix this screen wrote; anything else is a city the user typed.
    val usingDeviceLocation = stored.isCoordinatePair()

    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val fix = if (granted) context.lastKnownCoarseFix() else null
        if (fix != null) vm.edit { setWeatherLocation(fix) }
    }

    SettingsPage(modifier) {
        settingsNote(R.string.settings_weather_description_root)
        settingsGroup(key = "enabled") {
            SettingRow(
                title = stringResource(R.string.settings_show_weather),
                subtitle = stringResource(R.string.settings_show_weather_description),
                checked = enabled,
                onCheckedChange = { vm.edit { setWeatherEnabled(it) } },
            )
        }
        if (!enabled) return@SettingsPage

        settingsGroup(key = "device-location") {
            SettingRow(
                title = stringResource(R.string.settings_use_device_location),
                subtitle = stringResource(R.string.settings_use_device_location_description),
                checked = usingDeviceLocation,
                onCheckedChange = { on ->
                    // Off clears the fix, which puts the lookup back on the network address rather
                    // than leaving yesterday's coordinates behind to be wrong in a new city.
                    if (!on) vm.edit { setWeatherLocation("") }
                    else requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
            )
        }
        if (!usingDeviceLocation) {
            item(key = "location") { WeatherCityField(vm, stored) }
        }
        settingsGroup(key = "unit") {
            SettingRow(
                title = stringResource(R.string.settings_temperature_unit),
                subtitle = stringResource(R.string.settings_temperature_description),
                value = stringResource(
                    if (fahrenheit) R.string.settings_degree_fahrenheit
                    else R.string.settings_degree_celsius,
                ),
                onClick = { vm.edit { setWeatherFahrenheit(!fahrenheit) } },
            )
        }
    }
}

@Composable
private fun WeatherCityField(vm: SettingsViewModel, stored: String) {
    var text by remember(stored) { mutableStateOf(stored) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            vm.edit { setWeatherLocation(it) }
        },
        singleLine = true,
        label = { Text(stringResource(R.string.settings_custom_location)) },
        placeholder = { Text(stringResource(R.string.settings_location_hint)) },
        supportingText = { Text(stringResource(R.string.settings_custom_location_description)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapSmall),
    )
}

/** Core reads a `"lat,lon"` location straight through instead of geocoding it. */
private fun String.isCoordinatePair(): Boolean {
    val parts = split(',')
    return parts.size == 2 &&
        parts[0].trim().toDoubleOrNull() != null &&
        parts[1].trim().toDoubleOrNull() != null
}

/**
 * The last position the system already has, as `"lat,lon"`, or null when there is none.
 *
 * Weather is measured over tens of kilometres, so a cached coarse fix is as good an answer as a
 * fresh one and costs nothing — asking for a live update would wake the radios for a number that
 * would round to the same city anyway.
 */
private fun Context.lastKnownCoarseFix(): String? {
    if (
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED
    ) return null
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val fix = runCatching {
        manager.getProviders(true)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }.getOrNull() ?: return null
    // Locale.ROOT deliberately: this is a value for core's parser, not text for a person. A German
    // locale would format it "52,530,13,400" and the comma separator would no longer separate.
    return String.format(Locale.ROOT, "%.3f,%.3f", fix.latitude, fix.longitude)
}
