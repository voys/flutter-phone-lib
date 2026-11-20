package org.openvoipalliance.androidphoneintegration.call

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.audio.LocalDtmfToneGenerator
import org.openvoipalliance.androidphoneintegration.telecom.AndroidCallFramework
import org.openvoipalliance.androidphoneintegration.telecom.Connection
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.openvoipalliance.voiplib.Actions
import org.openvoipalliance.voiplib.VoIPLib
import org.openvoipalliance.voiplib.model.AttendedTransferSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallActionsTest {
    private val phoneLib = mockk<VoIPLib>()
    private val calls = Calls(mockk(relaxed = true), CallArrayList(Calls.MAX_CALLS))
    private val connection = mockk<Connection>(relaxed = true)
    private val androidCallFramework = mockk<AndroidCallFramework> { every { connection } returns this@CallActionsTest.connection }
    private val toneGenerator = mockk<LocalDtmfToneGenerator>(relaxed = true)
    private val actions = CallActions(phoneLib, calls, androidCallFramework, toneGenerator)
    private val call = voipLibCall()
    private val callActions = mockk<Actions>(relaxed = true)

    @Before
    fun setUp() {
        every { phoneLib.actions(any()) } returns callActions
    }

    @Test
    fun `call actions go through the telecom connection`() {
        actions.hold()
        actions.unhold()
        actions.toggleHold()
        actions.answer()
        actions.decline()
        actions.end()

        verify {
            connection.onHold()
            connection.onUnhold()
            connection.toggleHold()
            connection.onAnswer()
            connection.onReject()
            connection.onDisconnect()
        }
    }

    @Test
    fun `call actions do nothing without a connection`() {
        every { androidCallFramework.connection } returns null

        actions.hold()
        actions.answer()
        actions.end()

        verify(exactly = 0) {
            connection.onHold()
            connection.onAnswer()
            connection.onDisconnect()
        }
    }

    @Test
    fun `DTMF is sent on the active call and played locally when asked`() {
        calls.add(call)

        actions.sendDtmf('5')
        actions.sendDtmf('6', playToneLocally = false)

        verify {
            toneGenerator.play('5')
            phoneLib.actions(call)
            callActions.sendDtmf("5")
            callActions.sendDtmf("6")
        }
        verify(exactly = 0) { toneGenerator.play('6') }
    }

    @Test
    fun `DTMF is still played locally without a call`() {
        actions.sendDtmf('5')

        verify { toneGenerator.play('5') }
        verify(exactly = 0) { phoneLib.actions(any()) }
    }

    @Test
    fun `an attended transfer begins from the active call`() {
        calls.add(call)

        actions.beginAttendedTransfer("+31507654321")

        verify {
            phoneLib.actions(call)
            callActions.beginAttendedTransfer("+31507654321")
        }
    }

    @Test
    fun `an attended transfer is only begun with a call`() {
        actions.beginAttendedTransfer("+31507654321")

        verify(exactly = 0) { phoneLib.actions(any()) }
    }

    @Test
    fun `completing an attended transfer merges the transfer session`() {
        val target = voipLibCall()
        calls.add(call)
        calls.add(target)

        actions.completeAttendedTransfer()

        verify {
            phoneLib.actions(call)
            callActions.finishAttendedTransfer(AttendedTransferSession(target, call))
        }
    }

    @Test
    fun `completing does nothing outside a transfer`() {
        calls.add(call)

        actions.completeAttendedTransfer()

        verify(exactly = 0) { phoneLib.actions(any()) }
    }
}
