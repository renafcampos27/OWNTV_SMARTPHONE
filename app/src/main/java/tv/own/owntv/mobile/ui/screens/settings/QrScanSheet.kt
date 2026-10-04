package tv.own.owntv.mobile.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * Points the camera at the QR code the other device is showing and reads the address out of it.
 *
 * A convenience, never the only way: the same sheet offers the discovery list and a typed address,
 * because a camera can be refused, absent on a tablet, or simply useless in a dark living room. The
 * code carries the address alone — the PIN is still typed, so a photograph of the screen is not a
 * key to anything.
 */
@Composable
fun QrScanSheet(onScanned: (address: String, port: Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) request.launch(Manifest.permission.CAMERA) }

    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.local_sync_scan_qr)) {
        Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
            if (granted) {
                Note(stringResource(R.string.local_sync_scan_qr_description))
                CameraPreview(onScanned)
            } else {
                Note(stringResource(R.string.local_sync_camera_denied))
            }
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (String, Int) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onScanned)
    // Its own thread: analysis runs per frame, and doing it on the main one drops the preview.
    val executor = remember { Executors.newSingleThreadExecutor() }
    // One result is all we want. Without this the reader keeps firing while the sheet closes.
    val handled = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = MobileDimens.GapSmall),
        factory = { ctx ->
            val view = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    val text = image.decodeQr()
                    image.close()
                    if (text != null && handled.compareAndSet(false, true)) {
                        view.post { callback(hostOf(text), portOf(text)) }
                    }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}

/**
 * One frame, decoded. The Y plane alone is exactly what a QR reader wants — it is a black-and-white
 * pattern, and skipping the colour planes is what keeps this cheap enough to run on every frame.
 */
private fun ImageProxy.decodeQr(): String? = runCatching {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = PlanarYUVLuminanceSource(bytes, planes[0].rowStride, height, 0, 0, width, height, false)
    MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
}.getOrNull()

/** The companion QR encodes `http://<ip>:<port>/`; either half is pulled back out here. */
private fun hostOf(url: String): String =
    url.removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')

private fun portOf(url: String): Int =
    url.removePrefix("http://").removePrefix("https://").substringBefore('/')
        .substringAfter(':', "").toIntOrNull() ?: CompanionLink.DEFAULT_PORT
