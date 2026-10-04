package tv.own.owntv.mobile.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Which profile has been unlocked in this run of the app.
 *
 * Deliberately a ViewModel and deliberately **not** `rememberSaveable`. A phone rotates constantly
 * and changes font scale and dark mode on its own, and every one of those rebuilds the activity —
 * being asked for the PIN again each time would make a locked profile unusable. But saved state
 * comes back after Android kills a backgrounded app, and a restored "already unlocked" flag would
 * walk the next person straight past the PIN, which is the one thing the gate exists to stop. A
 * ViewModel has exactly the lifetime wanted: it survives a rebuild and dies with the process.
 */
class ProfileGateSession : ViewModel() {

    /** The profile unlocked in this session — never a bare "yes, someone unlocked something". */
    var unlockedProfileId by mutableStateOf<Long?>(null)
        private set

    fun unlock(profileId: Long) {
        unlockedProfileId = profileId
    }

    /**
     * A stale unlock can never authorise a different profile. Called as the active id arrives; a
     * null id is the flow not having emitted yet, which must not turn a rotation into a PIN prompt.
     */
    fun invalidateIfNotProfile(activeProfileId: Long?) {
        if (activeProfileId != null && unlockedProfileId != null && unlockedProfileId != activeProfileId) {
            unlockedProfileId = null
        }
    }
}
