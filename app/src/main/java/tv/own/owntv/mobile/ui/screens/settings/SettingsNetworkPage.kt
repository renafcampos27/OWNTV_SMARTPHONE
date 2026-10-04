package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import tv.own.owntv.core.network.DnsConfig
import tv.own.owntv.core.network.DohPresets
import tv.own.owntv.core.network.ProxyConfig
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * The app-wide proxy and DNS server.
 *
 * Both are forms with a Save, not live-writing rows: a proxy that is written on every keystroke goes
 * through "prox", "proxy.", "proxy.e"… and each of those half-addresses takes the whole app offline
 * for as long as it stands. Test uses what is typed, so a setting can be proven before it is kept.
 */
@Composable
fun SettingsNetworkPage(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val proxy = vm.settings.proxyConfig.pref(ProxyConfig())
    val dns = vm.settings.dnsConfig.pref(DnsConfig())
    val proxyTest = vm.proxyTest.pref(NetworkTestState.Idle)
    val dnsTest = vm.dnsTest.pref(NetworkTestState.Idle)

    var proxyOn by remember { mutableStateOf(proxy.enabled) }
    var host by remember { mutableStateOf(proxy.host) }
    var port by remember { mutableStateOf(proxy.port.toString()) }
    var user by remember { mutableStateOf(proxy.username) }
    var password by remember { mutableStateOf(proxy.password) }

    var dnsOn by remember { mutableStateOf(dns.enabled) }
    var dnsServer by remember { mutableStateOf(dns.serverText()) }

    // Seeded once from what is stored; after that the fields are the user's until Save writes them.
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(proxy, dns) {
        if (!seeded) {
            proxyOn = proxy.enabled
            host = proxy.host
            port = proxy.port.toString()
            user = proxy.username
            password = proxy.password
            dnsOn = dns.enabled
            dnsServer = dns.serverText()
            seeded = true
        }
    }

    SettingsPage(modifier) {
        settingsSection(R.string.settings_http_proxy)
        settingsNote(R.string.settings_proxy_description)
        settingsGroup(key = "proxy-on") {
            SettingRow(
                title = stringResource(R.string.settings_use_proxy),
                checked = proxyOn,
                onCheckedChange = { proxyOn = it },
            )
        }
        item(key = "proxy-form") {
            Column(Modifier.padding(horizontal = MobileDimens.ListRowPaddingH)) {
                MobileTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = stringResource(R.string.settings_host),
                    placeholder = stringResource(R.string.settings_proxy_host_hint),
                    imeAction = ImeAction.Next,
                )
                MobileTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    label = stringResource(R.string.settings_port),
                    placeholder = stringResource(R.string.settings_proxy_port_hint),
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                )
                MobileTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = stringResource(R.string.settings_username_optional),
                    imeAction = ImeAction.Next,
                )
                MobileTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_password_optional),
                    isPassword = true,
                )
                Row {
                    MobileButton(
                        text = stringResource(R.string.common_save),
                        onClick = {
                            vm.saveProxy(proxyOn, host, port.toIntOrNull() ?: 0, user, password)
                        },
                        modifier = Modifier.padding(vertical = MobileDimens.GapSmall),
                    )
                    MobileButton(
                        text = stringResource(R.string.settings_test_proxy),
                        style = MobileButtonStyle.SECONDARY,
                        enabled = proxyTest != NetworkTestState.Testing,
                        onClick = { vm.testProxy(host, port.toIntOrNull() ?: 0, user, password) },
                        modifier = Modifier.padding(
                            start = MobileDimens.GapSmall,
                            top = MobileDimens.GapSmall,
                            bottom = MobileDimens.GapSmall,
                        ),
                    )
                }
                TestResult(proxyTest, okRes = R.string.settings_proxy_connected)
            }
        }
        settingsNote(R.string.settings_proxy_privacy)
        settingsNote(R.string.settings_proxy_limitations)

        settingsSection(R.string.settings_dns_custom)
        settingsNote(R.string.settings_dns_toggle_description)
        settingsGroup(key = "dns-on") {
            SettingRow(
                title = stringResource(R.string.settings_dns_use_custom),
                checked = dnsOn,
                onCheckedChange = { dnsOn = it },
            )
        }
        item(key = "dns-form") {
            Column(Modifier.padding(horizontal = MobileDimens.ListRowPaddingH)) {
                // The three well-known DoH endpoints, so nobody has to type one from memory.
                Row {
                    DohPresets.all.forEach { (name, url) ->
                        TextButton(onClick = { dnsServer = url }) { Text(name) }
                    }
                }
                MobileTextField(
                    value = dnsServer,
                    onValueChange = { dnsServer = it },
                    label = stringResource(R.string.settings_dns_server),
                    placeholder = stringResource(R.string.settings_dns_server_hint),
                )
                Row {
                    MobileButton(
                        text = stringResource(R.string.common_save),
                        onClick = {
                            val (h, p, doh) = dnsServer.asDnsServer()
                            vm.saveDns(dnsOn && dnsServer.isNotBlank(), h, p, doh)
                        },
                        modifier = Modifier.padding(vertical = MobileDimens.GapSmall),
                    )
                    MobileButton(
                        text = stringResource(R.string.settings_dns_test),
                        style = MobileButtonStyle.SECONDARY,
                        enabled = dnsTest != NetworkTestState.Testing,
                        onClick = {
                            val (h, p, doh) = dnsServer.asDnsServer()
                            vm.testDns(h, p, doh)
                        },
                        modifier = Modifier.padding(
                            start = MobileDimens.GapSmall,
                            top = MobileDimens.GapSmall,
                            bottom = MobileDimens.GapSmall,
                        ),
                    )
                }
                TestResult(dnsTest, okRes = R.string.settings_dns_resolved)
                if (dnsOn && dnsServer.isBlank()) {
                    Text(
                        text = stringResource(R.string.settings_dns_server_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        settingsNote(R.string.settings_dns_explanation)
        settingsNote(R.string.settings_dns_limitations)
    }
}

/**
 * One field holds both DNS modes, the way the TV app's does: an `https://` address is DoH, anything
 * else is a plain server with an optional `:port`. Two fields would only ever have one filled in.
 */
private fun DnsConfig.serverText(): String = when {
    dohUrl.isNotBlank() -> dohUrl
    host.isBlank() -> ""
    port > 0 && port != DnsConfig.DNS_DEFAULT_PORT -> "$host:$port"
    else -> host
}

private fun String.asDnsServer(): Triple<String, Int, String> {
    val s = trim()
    if (s.startsWith("https://", ignoreCase = true)) {
        return Triple("", DnsConfig.DNS_DEFAULT_PORT, s)
    }
    // Only a single colon is a port — an IPv6 literal has several and keeps its default port.
    val colon = s.lastIndexOf(':')
    if (colon > 0 && s.indexOf(':') == colon) {
        val port = s.substring(colon + 1).toIntOrNull() ?: DnsConfig.DNS_DEFAULT_PORT
        return Triple(s.substring(0, colon).trim(), port, "")
    }
    return Triple(s, DnsConfig.DNS_DEFAULT_PORT, "")
}

/** One line under the buttons: testing, how fast it was, or why it failed. */
@Composable
private fun TestResult(state: NetworkTestState, okRes: Int) {
    val text = when (state) {
        NetworkTestState.Idle -> return
        NetworkTestState.Testing -> stringResource(R.string.settings_testing)
        is NetworkTestState.Ok -> stringResource(okRes, state.millis.toInt())
        is NetworkTestState.Failed ->
            state.argument?.let { stringResource(state.messageRes, it) }
                ?: stringResource(state.messageRes)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state is NetworkTestState.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
