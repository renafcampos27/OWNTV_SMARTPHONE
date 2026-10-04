package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.junit.Rule
import org.junit.Test

/**
 * Keyboard + focus regression tests for the phone text entry.
 *
 * Covers the acceptance criteria that can be asserted without a physical IME:
 * tapping focuses, typed/pasted/deleted text updates, Next/Done/Search actions behave,
 * and a focused field inside a scrolling IME-safe container stays visible.
 */
class MobileTextFieldTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tappingFieldFocusesIt() {
        rule.setContent {
            MaterialTheme {
                MobileTextField(
                    value = "",
                    onValueChange = {},
                    label = "Name",
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        rule.onNodeWithTag("field").performClick()
        rule.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun typedPastedAndDeletedTextUpdates() {
        var text by mutableStateOf("")
        rule.setContent {
            MaterialTheme {
                MobileTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Name",
                    modifier = Modifier.testTag("field"),
                )
            }
        }
        // Typed.
        rule.onNodeWithTag("field").performTextInput("Hello")
        rule.runOnIdle { assert(text == "Hello") }
        // Pasted (a multi-word replacement behaves like a paste).
        rule.onNodeWithTag("field").performTextClearance()
        rule.onNodeWithTag("field").performTextInput("Hello World pasted")
        rule.runOnIdle { assert(text == "Hello World pasted") }
        // Deleted.
        rule.onNodeWithTag("field").performTextClearance()
        rule.runOnIdle { assert(text.isEmpty()) }
    }

    @Test
    fun pinFieldKeepsDigitsOnly() {
        var pin by mutableStateOf("")
        rule.setContent {
            MaterialTheme {
                MobileTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6 && it.all(Char::isDigit)) pin = it
                    },
                    label = "PIN",
                    keyboardType = KeyboardType.NumberPassword,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.testTag("pin"),
                )
            }
        }
        // Digits are accepted, exactly as a numeric IME sends them.
        rule.onNodeWithTag("pin").performTextInput("1234")
        rule.runOnIdle { assert(pin == "1234") }
        // A non-digit edit is rejected whole rather than partially applied.
        rule.onNodeWithTag("pin").performTextClearance()
        rule.onNodeWithTag("pin").performTextInput("12ab")
        rule.runOnIdle { assert(pin.isEmpty()) }
    }

    @Test
    fun searchImeActionCallsOnSearch() {
        var searched = false
        rule.setContent {
            MaterialTheme {
                MobileTextField(
                    value = "cnn",
                    onValueChange = {},
                    label = "Search",
                    imeAction = ImeAction.Search,
                    onSearch = { searched = true },
                    modifier = Modifier.testTag("search"),
                )
            }
        }
        rule.onNodeWithTag("search").performClick()
        rule.onNodeWithTag("search").performImeAction()
        rule.runOnIdle { assert(searched) }
    }

    @Test
    fun nextImeActionMovesFocusForward() {
        val first = FocusRequester()
        val second = FocusRequester()
        rule.setContent {
            MaterialTheme {
                Column {
                    MobileTextField(
                        value = "",
                        onValueChange = {},
                        label = "First",
                        imeAction = ImeAction.Next,
                        modifier = Modifier
                            .testTag("first")
                            .focusRequester(first),
                    )
                    MobileTextField(
                        value = "",
                        onValueChange = {},
                        label = "Second",
                        imeAction = ImeAction.Done,
                        modifier = Modifier
                            .testTag("second")
                            .focusRequester(second),
                    )
                }
            }
        }
        rule.onNodeWithTag("first").performClick()
        rule.onNodeWithTag("first").assertIsFocused()
        rule.onNodeWithTag("first").performImeAction()
        rule.onNodeWithTag("second").assertIsFocused()
    }

    @Test
    fun focusedFieldInScrollingImeSafeFormStaysVisible() {
        rule.setContent {
            MaterialTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                ) {
                    repeat(20) {
                        MobileTextField(
                            value = "",
                            onValueChange = {},
                            label = "Field $it",
                            imeAction = if (it < 19) ImeAction.Next else ImeAction.Done,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("field-$it"),
                        )
                    }
                }
            }
        }
        // Scrolling forms keep every field reachable and focusable with the keyboard up.
        rule.onNodeWithTag("field-19").performScrollTo()
        rule.onNodeWithTag("field-19").performClick()
        rule.onNodeWithTag("field-19").assertIsFocused()
        rule.onNodeWithTag("field-19").assertIsDisplayed()
        rule.onNodeWithText("Field 19").assertIsDisplayed()
    }
}
