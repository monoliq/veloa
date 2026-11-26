package az.gesture.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun newerVersionComparisonHandlesSemVerAndPrefixes() {
        assertTrue(AppUpdateChecker.isNewerVersion("1.2.14", "1.2.13"))
        assertTrue(AppUpdateChecker.isNewerVersion("v1.2.14", "1.2.13"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.2.10", "1.2.9"))
        assertTrue(AppUpdateChecker.isNewerVersion("2.0", "1.9.9"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.2.13", "1.2.13"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.2.12", "1.2.13"))
    }

    @Test
    fun skippedVersionSuppressesOnlyThatExactVersion() {
        assertTrue(AppUpdateChecker.shouldPromptUpdate("1.2.14", "1.2.13", null))
        assertFalse(AppUpdateChecker.shouldPromptUpdate("1.2.14", "1.2.13", "1.2.14"))
        assertFalse(AppUpdateChecker.shouldPromptUpdate("v1.2.14", "1.2.13", "1.2.14"))
        // Newer release 1.2.15 must prompt even if 1.2.14 was permanently skipped
        assertTrue(AppUpdateChecker.shouldPromptUpdate("1.2.15", "1.2.13", "1.2.14"))
        // Not newer -> never prompt
        assertFalse(AppUpdateChecker.shouldPromptUpdate("1.2.13", "1.2.13", null))
    }

    @Test
    fun pendingUpdateBadgeStaysActiveUntilInstalledVersionCatchesUp() {
        assertTrue(AppUpdateChecker.hasPendingUpdate("1.2.17", "1.2.16"))
        assertTrue(AppUpdateChecker.hasPendingUpdate("v1.2.17", "1.2.16"))
        assertFalse(AppUpdateChecker.hasPendingUpdate("1.2.17", "1.2.17"))
        assertFalse(AppUpdateChecker.hasPendingUpdate("1.2.16", "1.2.17"))
        assertFalse(AppUpdateChecker.hasPendingUpdate(null, "1.2.17"))
        assertFalse(AppUpdateChecker.hasPendingUpdate("  ", "1.2.17"))
    }

    @Test
    fun releaseNotesFormatterStripsDownloadsAndCleansMarkdown() {
        val raw = """
            ## Veloa v1.2.14

            ### What's New
            - **Auto-Hide Trigger**: Side panel hides automatically while drawing.
            * **Bottom-Right Controls**: Added `Back` and quick-open menu.

            ### Downloads
            - **Veloa.apk** - Signed APK
        """.trimIndent()

        val formatted = AppUpdateChecker.formatReleaseNotes(raw)
        assertFalse(formatted.contains("Downloads"))
        assertFalse(formatted.contains("Veloa.apk"))
        assertFalse(formatted.contains("**"))
        assertFalse(formatted.contains("`"))
        assertTrue(formatted.contains("• Auto-Hide Trigger: Side panel hides automatically while drawing."))
        assertTrue(formatted.contains("• Bottom-Right Controls: Added Back and quick-open menu."))
        assertEquals(
            "What's New\n• Auto-Hide Trigger: Side panel hides automatically while drawing.\n• Bottom-Right Controls: Added Back and quick-open menu.",
            formatted
        )
    }
}
