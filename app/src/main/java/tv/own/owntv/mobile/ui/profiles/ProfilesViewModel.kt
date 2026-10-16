package tv.own.owntv.mobile.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.profile.ProfileManager
import tv.own.owntv.core.profile.ProfileAuthorizationRequired
import tv.own.owntv.core.settings.SettingsRepository

/** Who is watching: the list, the one in use, and the four things that can be done to a profile. */
class ProfilesViewModel(
    private val profileDao: ProfileDao,
    settings: SettingsRepository,
    private val manager: ProfileManager,
    private val context: android.content.Context,
) : ViewModel() {
    private val _mutation = MutableStateFlow(ProfileMutationState())
    val mutation = _mutation.asStateFlow()

    fun clearMutationError() {
        if (!_mutation.value.busy) _mutation.value = ProfileMutationState()
    }

    /** Reserve before launching so rapid taps cannot start two writers. Never expose exceptions/PINs. */
    private fun mutate(onSuccess: () -> Unit = {}, operation: suspend () -> Unit) {
        if (_mutation.value.busy) return
        _mutation.value = ProfileMutationState(busy = true)
        viewModelScope.launch {
            var succeeded = false
            try {
                operation()
                succeeded = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IncorrectProfilePin) {
                _mutation.value = ProfileMutationState(error = ProfileMutationError.WRONG_PIN)
            } catch (_: ProfileAuthorizationRequired) {
                _mutation.value = ProfileMutationState(error = ProfileMutationError.PROTECTION_CHANGED)
            } catch (_: ProfileCannotBeDeleted) {
                _mutation.value = ProfileMutationState(error = ProfileMutationError.CANNOT_DELETE)
            } catch (_: Exception) {
                _mutation.value = ProfileMutationState(error = ProfileMutationError.FAILED)
            } finally {
                if (_mutation.value.busy) _mutation.value = ProfileMutationState()
            }
            if (succeeded) onSuccess()
        }
    }

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
        var createdId = -1L
        mutate(onSuccess = { onCreated(createdId) }) {
            createdId = withContext(Dispatchers.IO) { manager.create(name, avatarId, isKids, pin, fallbackName) }
        }
    }

    /** [pin]: null keeps the existing PIN, "" removes it. */
    fun edit(profile: ProfileEntity, name: String, avatarId: Int, isKids: Boolean, pin: String?, currentPin: String, onSaved: () -> Unit) {
        mutate(onSuccess = onSaved) {
            withContext(Dispatchers.IO) {
                val current = profileDao.getById(profile.id) ?: throw ProfileAuthorizationRequired()
                val sensitive = !current.pinHash.isNullOrBlank() && (pin != null || current.isKids && !isKids)
                val grant = if (sensitive) manager.authorizeEdit(current.id, currentPin, isKids, pin)
                    ?: throw IncorrectProfilePin() else null
                manager.edit(current, name, avatarId, isKids, pin, grant)
            }
        }
    }

    /**
     * Give [profile] the picture at [uri] — whatever the photo picker handed back. Core copies and
     * scales it; failures stay in the editor's operation state rather than appearing to ignore a tap.
     */
    fun setPicture(profile: ProfileEntity, uri: android.net.Uri) {
        mutate {
            val ok = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { manager.setCustomAvatar(profile, it) } ?: false
            }
            if (!ok) throw IllegalStateException()
        }
    }

    /** Drop [profile]'s picture; the drawn tile it already had comes back. */
    fun clearPicture(profile: ProfileEntity) {
        mutate { withContext(Dispatchers.IO) { manager.clearCustomAvatar(profile) } }
    }

    fun delete(profile: ProfileEntity, currentPin: String, onDeleted: () -> Unit) {
        mutate(onSuccess = onDeleted) {
            withContext(Dispatchers.IO) {
                val current = profileDao.getById(profile.id) ?: throw ProfileAuthorizationRequired()
                val grant = if (!current.pinHash.isNullOrBlank()) manager.authorizeDelete(current.id, currentPin)
                    ?: throw IncorrectProfilePin() else null
                if (!manager.delete(current, grant)) throw ProfileCannotBeDeleted()
            }
        }
    }
}

data class ProfileMutationState(val busy: Boolean = false, val error: ProfileMutationError? = null)
enum class ProfileMutationError { WRONG_PIN, PROTECTION_CHANGED, CANNOT_DELETE, FAILED }
private class IncorrectProfilePin : Exception()
private class ProfileCannotBeDeleted : Exception()
