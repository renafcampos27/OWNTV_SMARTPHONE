package tv.own.owntv.mobile.ui.screens.live

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.*

/** Provider identities stay intact; display renames never alter automatic grouping. */
@Composable
fun ChannelVersionsSheet(channel: ChannelEntity, vm: LiveViewModel, playOnly: Boolean = true,
    onPlay: (ChannelEntity) -> Unit, onDismiss: () -> Unit) {
    val policy by vm.channelVersionSettings.collectAsStateWithLifecycle()
    var rows by remember(channel.id) { mutableStateOf<List<ChannelEntity>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    LaunchedEffect(channel.id, policy, playOnly) { rows = vm.channelVersions(channel, includeHidden = !playOnly) }
    LaunchedEffect(channel.id, query, policy) {
        kotlinx.coroutines.delay(180)
        candidates = vm.channelVersionCandidates(channel, query)
    }
    MobileBottomSheet(title = stringResource(R.string.channel_versions_title), onDismissRequest = onDismiss,
        modifier = Modifier.background(Color(0xFF151D2D))) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        surface = Color(0xFF151D2D), onSurface = Color.White, onSurfaceVariant = Color(0xFFCDD8EA))) {
    Column {
        if (!playOnly) {
            MobileListRow(title = stringResource(R.string.channel_versions_add), onClick = { adding = !adding })
            MobileListRow(title = stringResource(R.string.channel_versions_reset), onClick = { vm.saveChannelVersionOrder(channel, null) })
        }
        if (adding) {
            MobileTextField(value = query, onValueChange = { query = it }, label = stringResource(R.string.channel_versions_add_hint))
            LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
                itemsIndexed(candidates, key = { _, item -> item.id }) { _, candidate ->
                    MobileListRow(title = vm.displayName(candidate), onClick = { vm.addChannelVersion(channel, candidate); query = ""; adding = false })
                }
            }
        }
        if (rows == null) Text(stringResource(R.string.channel_versions_loading))
        else if (rows!!.isEmpty()) Text(stringResource(R.string.channel_versions_empty))
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
        itemsIndexed(rows.orEmpty(), key = { _, item -> item.id }) { index, item ->
            var focused by remember(item.id) { mutableStateOf(false) }
            val hidden = CustomizeKeys.channel(item) in policy.hiddenItems
            MobileListRow(
                modifier = Modifier.onFocusChanged { focused = it.hasFocus }
                    .background(if (focused) Color(0xFF264F82) else Color(0xFF151D2D))
                    .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent),
                title = vm.displayName(item),
                subtitle = if (hidden) stringResource(R.string.channel_versions_hidden) else null,
                selected = item.id == channel.id,
                leading = { Icon(MobileIcons.LiveTv, null, tint = Color.White) },
                onClick = { if (!hidden) { onPlay(item); onDismiss() } },
                trailing = if (playOnly) null else ({
                    Row {
                        if (index > 0) IconButton(onClick = {
                            val moved = rows.orEmpty().toMutableList().apply { add(index - 1, removeAt(index)) }
                            rows = moved; vm.saveChannelVersionOrder(channel, moved)
                        }) { Icon(MobileIcons.KeyboardArrowUp, stringResource(R.string.channel_versions_up), tint = Color.White) }
                        if (index < rows.orEmpty().lastIndex) IconButton(onClick = {
                            val moved = rows.orEmpty().toMutableList().apply { add(index + 1, removeAt(index)) }
                            rows = moved; vm.saveChannelVersionOrder(channel, moved)
                        }) { Icon(MobileIcons.KeyboardArrowDown, stringResource(R.string.channel_versions_down), tint = Color.White) }
                        if (CustomizeKeys.channel(item) in policy.channelVersionGroups) IconButton(onClick = { vm.removeManualChannelVersion(item) }) {
                            Icon(MobileIcons.Close, stringResource(R.string.channel_versions_unlink), tint = Color.White)
                        }
                    }
                }),
            )
        }
    }
}
}
}
}
