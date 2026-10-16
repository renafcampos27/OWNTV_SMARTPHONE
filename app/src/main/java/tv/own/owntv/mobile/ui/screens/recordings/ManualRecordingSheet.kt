package tv.own.owntv.mobile.ui.screens.recordings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ManualRecordingSheet(onDismiss: () -> Unit, vm: ManualRecordingViewModel = koinViewModel()) {
    val initial = remember { LocalDateTime.now().plusMinutes(5) }
    var date by rememberSaveable { mutableStateOf(initial.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))) }
    var start by rememberSaveable { mutableStateOf(initial.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var end by rememberSaveable { mutableStateOf(initial.plusHours(1).format(DateTimeFormatter.ofPattern("HH:mm"))) }
    val query by vm.query.collectAsStateWithLifecycle()
    val rows by vm.channels.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val scheduled by vm.scheduled.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.scheduled.value=false; vm.result.value=null }
    LaunchedEffect(scheduled) { if (scheduled) onDismiss() }
    MobileBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, title=stringResource(R.string.own_manual_title)) {
        Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.own_manual_zone, java.time.ZoneId.systemDefault().id),style=MaterialTheme.typography.bodySmall)
            MobileTextField(query, { vm.query.value=it }, stringResource(R.string.own_manual_channel),enabled=!busy)
            if (selected != null) Text(stringResource(R.string.own_manual_selected, selected!!.name),color=MaterialTheme.colorScheme.primary)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max=180.dp)) {
                items(rows.size, key = { rows[it].id }) { index ->
                    val ch = rows[index]
                    MobileListRow(title=ch.name, onClick={ if (!busy) vm.selected.value=ch })
                }
            }
            MobileTextField(date,{date=it},stringResource(R.string.own_manual_date),enabled=!busy)
            MobileTextField(start,{start=it},stringResource(R.string.own_manual_start),enabled=!busy)
            MobileTextField(end,{end=it},stringResource(R.string.own_manual_end),enabled=!busy)
            Text(stringResource(R.string.own_manual_overnight),style=MaterialTheme.typography.bodySmall)
            result?.let { Text(stringResource(it),color=MaterialTheme.colorScheme.error) }
            MobileButton(text=stringResource(if (busy) R.string.own_manual_working else R.string.own_manual_save),
                enabled=selected!=null && !busy,onClick={vm.schedule(date,start,end)})
        }
    }
}
