package tv.own.owntv.mobile.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * How the Cast SDK is configured. Found by class name from the manifest, so it is public, has a
 * no-argument constructor, and is kept by name in `proguard-rules.pro`.
 *
 * ### Which receiver, and why
 * [CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID] — Google's own receiver. It is free
 * and needs no account. The two alternatives both cost a one-time Google Cast developer registration:
 * a *styled* receiver, which only restyles the idle screen, and a *custom* receiver, which is a web
 * app of one's own and the only one that can run code on the television.
 *
 * The custom one is the only way to add request headers — and it still cannot set a `User-Agent`,
 * because a browser forbids overriding that on a request. Since `User-Agent` is the header IPTV
 * providers actually demand, and raw MPEG-TS is unplayable on every one of the three, paying for a
 * receiver would buy a handful of channels in exchange for a web app to host forever. So: the free
 * one, behind [RECEIVER_APP_ID], which is the single line to change if that ever stops being true.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(RECEIVER_APP_ID)
        // The notification and lock-screen controls are this app's own PlaybackService, hung on
        // core's MediaSession. Letting the SDK post a second set would put two media notifications
        // on screen for one stream, disagreeing with each other about the position.
        .setCastMediaOptions(
            com.google.android.gms.cast.framework.media.CastMediaOptions.Builder()
                .setMediaSessionEnabled(false)
                .setNotificationOptions(null)
                .build(),
        )
        // Ending the session must not stop the television dead: playback comes back to the phone,
        // and the receiver going idle by itself is what makes that a clean handover.
        .setStopReceiverApplicationWhenEndingSession(true)
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    companion object {
        /** Google's free default media receiver. See the class doc before changing it. */
        const val RECEIVER_APP_ID: String = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
    }
}
