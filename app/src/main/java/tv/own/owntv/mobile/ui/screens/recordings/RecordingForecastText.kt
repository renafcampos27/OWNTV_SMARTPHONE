package tv.own.owntv.mobile.ui.screens.recordings

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.core.recording.RecordingSpaceForecast
import java.text.NumberFormat

@Composable
internal fun RecordingForecastText(forecast: RecordingSpaceForecast) {
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
    fun size(bytes: Long) = format.format(bytes / 1_073_741_824.0)
    Text(stringResource(R.string.own_recording_forecast, size(forecast.lowerBytes), size(forecast.upperBytes)),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH))
    Text(stringResource(if (forecast.measured) R.string.own_recording_forecast_measured else R.string.own_recording_forecast_generic),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH))
    Text(forecast.availableBytes?.let { stringResource(R.string.own_recording_forecast_available, size(it)) }
        ?: stringResource(R.string.own_recording_forecast_unknown),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH))
    if (forecast.mayExceedSpace) Text(stringResource(R.string.own_recording_forecast_warning),
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH))
}
