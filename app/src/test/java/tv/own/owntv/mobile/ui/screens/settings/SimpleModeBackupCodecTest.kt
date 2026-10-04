package tv.own.owntv.mobile.ui.screens.settings
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class SimpleModeBackupCodecTest {
 @Test fun `all mobile mode fields survive backup roundtrip`() {
  val options = SimpleModeOptions(hideCategories=true, hideSidebar=true, channelRecovery=false, recoveryTimeoutSeconds=17)
  assertEquals(options, SimpleModeBackupCodec.decode(SimpleModeBackupCodec.encode(options), SimpleModeOptions()))
 }
 @Test fun `missing fields retain current values`() {
  val options=SimpleModeOptions(hideCategories=true, recoveryTimeoutSeconds=17)
  assertEquals(options, SimpleModeBackupCodec.decode(JSONObject().put("version",1),options))
 }
 @Test(expected=IllegalArgumentException::class) fun `invalid recovery timeout rejects restore`() {
  SimpleModeBackupCodec.decode(JSONObject().put("version",1).put("recoveryTimeoutSeconds",0), SimpleModeOptions())
 }
 @Test fun `TV boot automation is not enabled on a phone by restore`() {
  val restored=SimpleModeBackupCodec.decode(JSONObject().put("version",1).put("startOnBoot",true).put("startOnWake",true), SimpleModeOptions())
  assertFalse(restored.startOnBoot); assertFalse(restored.startOnWake)
 }
 @Test fun `timeouts normalized before saving`() { assertEquals(1,RecoveryTimeout.normalize(-2)); assertEquals(60,RecoveryTimeout.normalize(900)); assertEquals(3,RecoveryTimeout.normalize(null)) }
}
