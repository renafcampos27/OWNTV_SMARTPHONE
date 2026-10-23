package tv.own.owntv.mobile.ui.stage

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.mobile.ui.components.*
import tv.own.owntv.mobile.ui.theme.MobileTheme

@Preview(name = "Stage — telefone", widthDp = 360, heightDp = 720, showBackground = true)
@Composable private fun PhoneStage() = StageSample(false)
@Preview(name = "Stage — claro / texto grande", widthDp = 360, heightDp = 720, fontScale = 1.4f, showBackground = true)
@Composable private fun LightStage() = StageSample(true)
@Preview(name = "Stage — tablet", widthDp = 800, heightDp = 600, showBackground = true)
@Composable private fun TabletStage() = StageSample(false)

@Composable private fun StageSample(light: Boolean) {
    MobileTheme(if (light) ThemeMode.LIGHT else ThemeMode.DARK, AccentColor.TEAL) {
        Column(Modifier.fillMaxSize().stageBackdrop().padding(12.dp)) {
            Text("Canais", style = MaterialTheme.typography.titleLarge)
            FilterChipRow(listOf("Todos", "Portugal", "Documentários"), 1, {})
            MobileListRow("RTP 1", subtitle = "Programa atual", selected = true,
                leading = { Icon(MobileIcons.LiveTv, null) }, trailing = { Icon(MobileIcons.History, null) })
            MobileListRow("SIC HD", subtitle = "Programa seguinte", leading = { Icon(MobileIcons.LiveTv, null) })
            MobileListRow("Odisseia", subtitle = "Documentário", leading = { Icon(MobileIcons.LiveTv, null) })
            Spacer(Modifier.height(16.dp))
            MobileButton("Guia TV", {})
            SettingRow("Gravações", value = "Abrir", showChevron = true)
            SettingRow("Definições", showChevron = true)
        }
    }
}
