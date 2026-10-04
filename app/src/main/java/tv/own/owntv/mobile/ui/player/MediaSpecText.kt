package tv.own.owntv.mobile.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.player.DecoderSpec
import tv.own.owntv.player.MediaSpec

/**
 * The one line under a playback error: codec, resolution and which decoder actually ran it.
 *
 * Hardware or software, direct or through the GPU — that is the difference between "this file is
 * broken" and "this phone cannot decode this file", and it is the first thing worth knowing from a
 * report. Worded exactly as the television words it, from the same strings.
 */
@Composable
fun MediaSpec.displayText(): String {
    val separator = stringResource(R.string.player_metadata_separator)
    val decoderText = decoder?.let {
        when (it) {
            is DecoderSpec.Hardware -> buildList {
                add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }
            is DecoderSpec.Software -> buildList {
                add(stringResource(R.string.player_decoder_software))
                if (it.gpu) add(stringResource(R.string.player_decoder_gpu))
            }
            is DecoderSpec.Named -> buildList {
                add(
                    when (it.value.lowercase()) {
                        "exoplayer" -> stringResource(R.string.settings_player_exoplayer)
                        "mpv" -> stringResource(R.string.settings_player_mpv)
                        else -> it.value
                    },
                )
                if (it.hardware) add(stringResource(R.string.player_decoder_hardware))
                if (it.direct) add(stringResource(R.string.player_decoder_direct))
            }
        }.joinToString(separator)
    }
    return listOfNotNull(codec, resolution, decoderText).joinToString(separator)
}
