package org.openvoipalliance.voiplib

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.linphone.core.AudioDevice
import org.linphone.core.Reason
import org.openvoipalliance.androidphoneintegration.installKoin
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.openvoipalliance.voiplib.model.AttendedTransferSession
import org.openvoipalliance.voiplib.repository.call.controls.LinphoneSipActiveCallControlsRepository
import org.openvoipalliance.voiplib.repository.call.session.LinphoneSipSessionRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActionsTest {
    private val controls = mockk<LinphoneSipActiveCallControlsRepository>(relaxed = true)
    private val session = mockk<LinphoneSipSessionRepository>(relaxed = true)
    private val call = voipLibCall()
    private lateinit var actions: Actions

    @Before
    fun setUp() {
        installKoin {
            single { controls }
            single { session }
        }
        actions = Actions(call)
    }

    @Test
    fun `call session actions go through the session repository`() {
        actions.accept()
        actions.decline(Reason.Busy)
        actions.end()

        verify {
            session.acceptIncoming(call)
            session.declineIncoming(call, Reason.Busy)
            session.end(call)
        }
    }

    @Test
    fun `call control actions go through the call controls repository`() {
        every { controls.provideCallInfo(call) } returns "{}"

        actions.pause()
        actions.resume()
        actions.hold(true)
        actions.transferUnattended("+31507654321")
        actions.sendDtmf("123")

        verify {
            controls.pauseCall(call)
            controls.resumeCall(call)
            controls.setHold(call, true)
            controls.transferUnattended(call, "+31507654321")
            controls.sendDtmf(call, "123")
        }
        assertEquals("{}", actions.callInfo())
    }

    @Test
    fun `audio is routed to the matching device types`() {
        actions.routeAudioToEarpiece(call)
        actions.routeAudioToSpeaker(call)
        actions.routeAudioToBluetooth(call)
        actions.routeAudioToHeadset(call)

        verifyOrder {
            controls.routeAudioTo(listOf(AudioDevice.Type.Earpiece), call.linphoneCall)
            controls.routeAudioTo(listOf(AudioDevice.Type.Speaker), call.linphoneCall)
            controls.routeAudioTo(listOf(AudioDevice.Type.Bluetooth), call.linphoneCall)
            controls.routeAudioTo(
                listOf(AudioDevice.Type.Headphones, AudioDevice.Type.Headset),
                call.linphoneCall,
            )
        }
    }

    @Test
    fun `an attended transfer holds this call and calls the target`() {
        val target = voipLibCall()
        every { session.callTo("+31507654321") } returns target

        val transfer = actions.beginAttendedTransfer("+31507654321")

        assertEquals(AttendedTransferSession(call, target), transfer)
        verifyOrder {
            controls.pauseCall(call)
            session.callTo("+31507654321")
            controls.resumeCall(target)
        }

        actions.finishAttendedTransfer(transfer)
        verify { controls.finishAttendedTransfer(transfer) }
    }
}
