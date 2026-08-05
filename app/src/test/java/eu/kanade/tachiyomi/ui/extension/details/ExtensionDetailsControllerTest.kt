package eu.kanade.tachiyomi.ui.extension.details

import androidx.preference.EditTextPreference
import androidx.preference.Preference
import eu.kanade.tachiyomi.widget.preference.EditTextResetPreference
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionDetailsControllerTest {
    @Test
    fun `edit text dialog forwards accepted change to source preference`() {
        val source = mockk<EditTextPreference>()
        val dialog = mockk<EditTextResetPreference>()
        val listener = slot<Preference.OnPreferenceChangeListener>()
        every { dialog.setOnPreferenceChangeListener(capture(listener)) } just Runs
        every { source.callChangeListener("https://example.com/2") } returns true

        forwardEditTextPreferenceChange(dialog, source)

        assertTrue(listener.captured.onPreferenceChange(dialog, "https://example.com/2"))
        verify(exactly = 1) { source.callChangeListener("https://example.com/2") }
    }

    @Test
    fun `edit text dialog propagates rejected change`() {
        val source = mockk<EditTextPreference>()
        val dialog = mockk<EditTextResetPreference>()
        val listener = slot<Preference.OnPreferenceChangeListener>()
        every { dialog.setOnPreferenceChangeListener(capture(listener)) } just Runs
        every { source.callChangeListener("invalid") } returns false

        forwardEditTextPreferenceChange(dialog, source)

        assertFalse(listener.captured.onPreferenceChange(dialog, "invalid"))
        verify(exactly = 1) { source.callChangeListener("invalid") }
    }
}
