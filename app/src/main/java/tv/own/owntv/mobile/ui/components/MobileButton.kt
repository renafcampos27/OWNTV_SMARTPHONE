package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** How much weight a button carries on the screen it sits on. */
enum class MobileButtonStyle { PRIMARY, SECONDARY, TEXT }

/**
 * A button at the accessibility floor of 48 dp, whatever the label.
 *
 * The three styles exist because a screen usually has one action worth pressing, one worth
 * offering, and one worth mentioning — Play, Download, Cancel.
 */
@Composable
fun MobileButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MobileButtonStyle = MobileButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val sized = modifier.defaultMinSize(minHeight = MobileDimens.TouchTarget)
    val label: @Composable () -> Unit = {
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (style) {
        MobileButtonStyle.PRIMARY ->
            Button(onClick = onClick, modifier = sized, enabled = enabled) { label() }
        MobileButtonStyle.SECONDARY ->
            OutlinedButton(onClick = onClick, modifier = sized, enabled = enabled) { label() }
        MobileButtonStyle.TEXT ->
            TextButton(onClick = onClick, modifier = sized, enabled = enabled) { label() }
    }
}
