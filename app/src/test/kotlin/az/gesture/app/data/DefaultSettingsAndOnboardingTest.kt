package az.gesture.app.data

import az.gesture.app.ui.components.InitialPermissionStep
import az.gesture.app.ui.components.buildInitialPermissionSteps
import az.gesture.app.ui.components.nextPendingInitialPermissionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultSettingsAndOnboardingTest {

    @Test
    fun `default settings use side panel and open then draw mode`() {
        val defaults = ButtonSettings()
        assertEquals(HandlerType.SIDE, defaults.handlerType)
        assertEquals(SideTriggerMode.OPEN_THEN_DRAW, defaults.sideTriggerMode)
        assertEquals(HandlerType.SIDE, HandlerType.safeValueOf(null))
        assertEquals(SideTriggerMode.OPEN_THEN_DRAW, SideTriggerMode.safeValueOf(null))
    }

    @Test
    fun `initial permission steps include xiaomi popup last when supported`() {
        val steps = buildInitialPermissionSteps(hasXiaomiPopup = true)
        assertEquals(
            listOf(
                InitialPermissionStep.OVERLAY,
                InitialPermissionStep.BATTERY,
                InitialPermissionStep.AUTOSTART,
                InitialPermissionStep.XIAOMI_POPUP
            ),
            steps
        )
    }

    @Test
    fun `initial permission steps omit xiaomi popup on non-xiaomi devices`() {
        val steps = buildInitialPermissionSteps(hasXiaomiPopup = false)
        assertEquals(
            listOf(
                InitialPermissionStep.OVERLAY,
                InitialPermissionStep.BATTERY,
                InitialPermissionStep.AUTOSTART
            ),
            steps
        )
    }

    @Test
    fun `nextPendingInitialPermissionStep advances across granted and skipped steps`() {
        val steps = buildInitialPermissionSteps(hasXiaomiPopup = true)

        assertEquals(
            InitialPermissionStep.OVERLAY,
            nextPendingInitialPermissionStep(steps, emptySet(), emptySet())
        )
        assertEquals(
            InitialPermissionStep.BATTERY,
            nextPendingInitialPermissionStep(
                steps,
                granted = setOf(InitialPermissionStep.OVERLAY),
                skipped = emptySet()
            )
        )
        assertEquals(
            InitialPermissionStep.AUTOSTART,
            nextPendingInitialPermissionStep(
                steps,
                granted = setOf(InitialPermissionStep.OVERLAY),
                skipped = setOf(InitialPermissionStep.BATTERY)
            )
        )
        assertEquals(
            InitialPermissionStep.XIAOMI_POPUP,
            nextPendingInitialPermissionStep(
                steps,
                granted = setOf(InitialPermissionStep.OVERLAY, InitialPermissionStep.BATTERY),
                skipped = setOf(InitialPermissionStep.AUTOSTART)
            )
        )
        assertNull(
            nextPendingInitialPermissionStep(
                steps,
                granted = setOf(InitialPermissionStep.OVERLAY, InitialPermissionStep.BATTERY),
                skipped = setOf(InitialPermissionStep.AUTOSTART, InitialPermissionStep.XIAOMI_POPUP)
            )
        )
    }
}
