package tv.own.owntv.mobile.ui.profiles

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.profile.PROFILE_AVATAR_COUNT
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileSwitch
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** A PIN is four to six digits — long enough to be a lock, short enough to type one-handed. */
private const val PIN_MIN = 4
private const val PIN_MAX = 6

private val AVATAR_PICK_SIZE = 52.dp

/**
 * Ask for a profile's PIN. [onSubmit] returns false when the digits were wrong, which keeps the
 * sheet open and says so rather than dropping the user back where they started with no explanation.
 *
 * A sheet, not a centred dialog: that is what every prompt in this app is, and it is the only form
 * that frosts the same wallpaper the rest of the app frosts — a dialog is its own window and cannot.
 * It also rises to the thumb instead of sitting where the keyboard will cover it.
 */
@Composable
fun ProfilePinSheet(
    profileName: String,
    onSubmit: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        if (pin.length in PIN_MIN..PIN_MAX) {
            if (!onSubmit(pin)) { wrong = true; pin = "" } else keyboard?.hide()
        }
    }
    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.profiles_enter_pin, profileName),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
        ) {
            MobileTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= PIN_MAX && it.all(Char::isDigit)) {
                        pin = it
                        wrong = false
                    }
                },
                label = stringResource(R.string.profiles_pin),
                placeholder = stringResource(R.string.profiles_pin_placeholder),
                isPassword = true,
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
                isError = wrong,
                supportingText = if (wrong) stringResource(R.string.profiles_wrong_pin) else null,
                onImeDone = { submit() },
            )
        }
        SheetButtons(
            confirm = stringResource(R.string.common_ok),
            confirmEnabled = pin.length >= PIN_MIN,
            // Deliberately not dismissed here: a wrong PIN has to keep the sheet up to say so.
            onConfirm = { submit() },
            onDismiss = {
                keyboard?.hide()
                focusManager.clearFocus()
                onDismiss()
            },
        )
    }
}

/**
 * Create or edit a profile: [initial] non-null is an edit.
 *
 * [onConfirm]'s pin argument follows core's convention — null leaves the PIN alone, "" removes the
 * lock, anything else sets it.
 *
 * [takenNames] are the other profiles' names, lowercased. Names have to be unique because a backup
 * merges by name, so a collision blocks the button with the reason shown under the field.
 */
@Composable
fun ProfileEditorSheet(
    initial: ProfileEntity?,
    takenNames: Set<String>,
    onConfirm: (name: String, avatarId: Int, isKids: Boolean, pin: String?) -> Unit,
    onDismiss: () -> Unit,
    extraAction: (() -> Unit)? = null,
    // A picture of the user's own, which applies immediately rather than on Save — the file is
    // copied the moment it is chosen, so there is nothing sensible to do with it if the sheet is
    // then cancelled. Absent on a profile that does not exist yet: it has no id to store one under.
    customPath: String = "",
    onPickPicture: ((android.net.Uri) -> Unit)? = null,
    onClearPicture: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var avatarId by remember { mutableIntStateOf(initial?.avatarId ?: -1) }
    var isKids by remember { mutableStateOf(initial?.isKids == true) }
    var pin by remember { mutableStateOf("") }
    var removePin by remember { mutableStateOf(false) }
    val nameTaken = name.trim().isNotEmpty() && name.trim().lowercase() in takenNames

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(if (initial == null) R.string.profiles_new else R.string.profiles_edit),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            MobileTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.profiles_name),
                placeholder = stringResource(R.string.profiles_name_hint),
                imeAction = ImeAction.Next,
                isError = nameTaken,
                supportingText = if (nameTaken) stringResource(R.string.profiles_name_taken) else null,
            )
            Text(
                text = stringResource(R.string.profiles_avatar),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onPickPicture != null) {
                val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                ) { uri -> uri?.let(onPickPicture) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(
                        avatarId = avatarId,
                        imagePath = customPath,
                        modifier = Modifier
                            .size(AVATAR_PICK_SIZE - 8.dp)
                            .clip(CircleShape)
                            .clickable {
                                picker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                    )
                    MobileButton(
                        text = stringResource(R.string.profiles_avatar_own_picture),
                        onClick = {
                            picker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        style = MobileButtonStyle.TEXT,
                    )
                    if (customPath.isNotBlank() && onClearPicture != null) {
                        MobileButton(
                            text = stringResource(R.string.common_clear),
                            onClick = onClearPicture,
                            style = MobileButtonStyle.TEXT,
                        )
                    }
                }
            }
            // -1 is "no picture", and it is offered first so a profile can be left plain.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                items((-1 until PROFILE_AVATAR_COUNT).toList()) { id ->
                    val selected = id == avatarId
                    Box(
                        modifier = Modifier
                            .size(AVATAR_PICK_SIZE)
                            .clip(CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { avatarId = id }
                            .padding(4.dp),
                    ) {
                        ProfileAvatar(
                            avatarId = id,
                            modifier = Modifier.size(AVATAR_PICK_SIZE - 8.dp).clip(CircleShape),
                        )
                    }
                }
            }
            ToggleRow(
                label = stringResource(R.string.profiles_kids),
                description = stringResource(R.string.profiles_kids_description),
                checked = isKids,
                onToggle = { isKids = it },
            )
            if (initial?.pinHash != null) {
                ToggleRow(
                    label = stringResource(R.string.profiles_remove_pin),
                    description = stringResource(R.string.profiles_no_pin),
                    checked = removePin,
                    onToggle = { removePin = it },
                )
            }
            if (!removePin) {
                MobileTextField(
                    value = pin,
                    onValueChange = { if (it.length <= PIN_MAX && it.all(Char::isDigit)) pin = it },
                    label = stringResource(
                        if (initial?.pinHash != null) R.string.profiles_change_pin else R.string.profiles_optional_pin,
                    ),
                    placeholder = stringResource(R.string.profiles_pin_digits),
                    isPassword = true,
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                )
            }
        }
        SheetButtons(
            confirm = stringResource(if (initial == null) R.string.profiles_create else R.string.profiles_save),
            confirmEnabled = name.isNotBlank() && !nameTaken &&
                (removePin || pin.isEmpty() || pin.length >= PIN_MIN),
            onConfirm = {
                onConfirm(name.trim(), avatarId, isKids, if (removePin) "" else pin.takeIf { it.isNotBlank() })
                onDismiss()
            },
            onDismiss = onDismiss,
            // Delete stands beside Cancel rather than under the fields, where a thumb reaching for
            // the PIN box would find it.
            destructive = extraAction,
        )
    }
}

/** A label, a line of explanation and a switch — the whole row is the button. */
@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Flat: a sheet is already a pane of glass, so a second surface inside it only muddies it.
            .clickable { onToggle(!checked) }
            .padding(vertical = MobileDimens.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MobileSwitch(checked = checked)
    }
}

/** Cancel, an optional destructive action and confirm — the shape every sheet here ends in. */
@Composable
private fun SheetButtons(
    confirm: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (destructive != null) {
            MobileButton(
                text = stringResource(R.string.common_delete),
                onClick = destructive,
                style = MobileButtonStyle.TEXT,
            )
        }
        MobileButton(
            text = stringResource(R.string.common_cancel),
            onClick = onDismiss,
            style = MobileButtonStyle.TEXT,
        )
        MobileButton(text = confirm, onClick = onConfirm, enabled = confirmEnabled)
    }
}
