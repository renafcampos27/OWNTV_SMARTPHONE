package tv.own.owntv.mobile.ui.components

import androidx.compose.ui.text.input.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordKeyboardTest {
    @Test fun secretTextAndPinsUsePasswordImeTypes() {
        assertEquals(KeyboardType.Password, protectedKeyboardType(KeyboardType.Text, true))
        assertEquals(KeyboardType.NumberPassword, protectedKeyboardType(KeyboardType.Number, true))
        assertEquals(KeyboardType.NumberPassword, protectedKeyboardType(KeyboardType.NumberPassword, true))
    }
    @Test fun ordinarySearchAndUrlFieldsKeepTheirRequestedKeyboard() {
        assertEquals(KeyboardType.Text, protectedKeyboardType(KeyboardType.Text, false))
        assertEquals(KeyboardType.Uri, protectedKeyboardType(KeyboardType.Uri, false))
        assertEquals(KeyboardType.Number, protectedKeyboardType(KeyboardType.Number, false))
    }
}
