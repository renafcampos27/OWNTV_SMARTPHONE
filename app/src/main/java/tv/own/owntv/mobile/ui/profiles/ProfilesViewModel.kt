package tv.own.owntv.mobile.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.profile.ProfileManager
import tv.own.owntv.core.settings.SettingsRepository

/** Who is watching: the list, the one in use, and the four things that can be done to a profile. */
class ProfilesViewModel(
    profileDao: ProfileDao,
    settings: SettingsRepository,
    private val manager: ProfileManager,
    private val context: android.content.Context,
) : ViewModel() {

    /**
     * Null until Room has answered, never an empty list standing in for "not loaded yet". The gate
     * decides whether to show itself from this, and an empty answer arriving early would let the app
     * walk straight past a PIN.
     */
    val profiles: StateFlow<List<ProfileEntity>?> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The active profile, or null while the preference is still being read.
     *
     * Nullable on purpose, exactly as the television's is: `-1` already means "no profile chosen",
     * so a `-1` placeholder while loading makes those two states indistinguishable — and the shell
     * has to tell them apart, or the frame before the answer arrives looks like a fresh install and
     * flashes "Who is watching?" on the way past.
     */
    val activeProfileId: StateFlow<Long?> = settings.activeProfileId
        .map<Long, Long?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun verifyPin(profile: ProfileEntity, pin: String): Boolean = manager.verifyPin(profile, pin)

    /** Make [profile] the one in use; [onSwitched] runs once the preference has actually committed. */
    fun switchTo(profile: ProfileEntity, onSwitched: () -> Unit = {}) {
        viewModelScope.launch {
            manager.switchTo(profile.id)
            onSwitched()
        }
    }

    fun create(name: String, avatarId: Int, isKids: Boolean, pin: String?, fallbackName: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch { onCreated(manager.create(name, avatarId, isKids, pin, fallbackName)) }
    }

    /** [pin]: null keeps the existing PIN, "" removes it. */
    fun edit(profile: ProfileEntity, name: String, avatarId: Int, isKids: Boolean, pin: String?) {
        viewModelScope.launch { manager.edit(profile, name, avatarId, isKids, pin) }
    }

    /**
     * Give [profile] the picture at [uri] — whatever the photo picker handed back. Core copies and
     * scales it; [onResult] says whether it was a readable image, so the sheet can tell the user
     * rather than appearing to have ignored the tap.
     */
    fun setPicture(profile: ProfileEntity, uri: android.net.Uri, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { manager.setCustomAvatar(profile, it) } ?: false
            }.getOrDefault(false)
            onResult(ok)
        }
    }

    /** Drop [profile]'s picture; the drawn tile it already had comes back. */
    fun clearPicture(profile: ProfileEntity) {
        viewModelScope.launch { manager.clearCustomAvatar(profile) }
    }

    fun delete(profile: ProfileEntity) {
        viewModelScope.launch { manager.delete(profile) }
    }
}
