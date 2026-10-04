package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.profiles.ProfileAvatar
import tv.own.owntv.mobile.ui.profiles.ProfileEditorSheet
import tv.own.owntv.mobile.ui.profiles.ProfileGateSession
import tv.own.owntv.mobile.ui.profiles.ProfilePinSheet
import tv.own.owntv.mobile.ui.profiles.ProfilesViewModel
import tv.own.owntv.mobile.ui.screens.settings.customize.ConfirmDialog

/** The picture is the size of a list row's icon, so the rows keep the page's rhythm. */
private val ROW_AVATAR_SIZE = 36.dp

/**
 * Who is watching, and who may. Tapping a profile switches to it — asking for the PIN first if it
 * has one — and a long press opens it for editing. The last profile cannot be deleted: an app with
 * no profile has nobody to show anything to.
 */
@Composable
fun SettingsProfilePage(
    modifier: Modifier = Modifier,
    vm: ProfilesViewModel = koinViewModel(),
    gateSession: ProfileGateSession = koinViewModel(),
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeId by vm.activeProfileId.collectAsStateWithLifecycle()
    val fallbackName = stringResource(R.string.profiles_default_name)

    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }

    val list = profiles.orEmpty()

    // Switching here is the same authentication boundary as the launch chooser, so the session is
    // told about it — otherwise the next rotation would bounce the user back to "Who's watching?".
    fun switchTo(profile: ProfileEntity) = vm.switchTo(profile) { gateSession.unlock(profile.id) }

    SettingsPage(modifier) {
        settingsSection(R.string.profiles_title) {
            list.forEach { profile ->
                MobileListRow(
                    title = profile.name,
                    subtitle = when {
                        profile.isKids -> stringResource(R.string.profiles_kids_tag)
                        profile.pinHash != null -> stringResource(R.string.profiles_locked_tag)
                        else -> null
                    },
                    leading = {
                        ProfileAvatar(
                            avatarId = profile.avatarId,
                            imagePath = profile.avatarPath.orEmpty(),
                            modifier = Modifier.size(ROW_AVATAR_SIZE).clip(CircleShape),
                        )
                    },
                    onClick = {
                        when {
                            profile.id == activeId -> editing = profile
                            profile.pinHash != null -> pinFor = profile
                            else -> switchTo(profile)
                        }
                    },
                    onLongClick = { editing = profile },
                    selected = profile.id == activeId,
                    trailing = if (profile.id == activeId) {
                        {
                            Icon(
                                imageVector = MobileIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            MobileListRow(
                title = stringResource(R.string.profiles_add_button),
                leading = { Icon(MobileIcons.Add, contentDescription = null) },
                onClick = { adding = true },
            )
        }
    }

    // A locked profile is unlocked before it is switched into, not after: the point of the lock is
    // that its library never appears without the PIN.
    pinFor?.let { locked ->
        ProfilePinSheet(
            profileName = locked.name,
            onSubmit = { pin ->
                vm.verifyPin(locked, pin).also { if (it) { pinFor = null; switchTo(locked) } }
            },
            onDismiss = { pinFor = null },
        )
    }
    if (adding) {
        ProfileEditorSheet(
            initial = null,
            takenNames = list.map { it.name.lowercase() }.toSet(),
            onConfirm = { name, avatarId, isKids, pin -> vm.create(name, avatarId, isKids, pin, fallbackName) },
            onDismiss = { adding = false },
        )
    }
    editing?.let { profile ->
        ProfileEditorSheet(
            initial = profile,
            takenNames = list.filter { it.id != profile.id }.map { it.name.lowercase() }.toSet(),
            onConfirm = { name, avatarId, isKids, pin -> vm.edit(profile, name, avatarId, isKids, pin) },
            onDismiss = { editing = null },
            customPath = profile.avatarPath.orEmpty(),
            onPickPicture = { uri -> vm.setPicture(profile, uri) },
            onClearPicture = { vm.clearPicture(profile) },
            extraAction = if (list.size > 1) {
                { deleting = profile }
            } else {
                null
            },
        )
    }
    deleting?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.profiles_delete_title, profile.name),
            message = stringResource(R.string.profiles_delete_message),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { vm.delete(profile); deleting = null; editing = null },
            onDismiss = { deleting = null },
        )
    }
}
