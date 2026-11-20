package org.openvoipalliance.androidphoneintegration.telecom

import android.Manifest
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.call.CallArrayList
import org.openvoipalliance.androidphoneintegration.call.Calls
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.voipLibCall
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidCallFrameworkTest {
    private val telecomManager = mockk<TelecomManager>(relaxed = true)
    private val calls = Calls(mockk(relaxed = true), CallArrayList(Calls.MAX_CALLS))
    private val account = slot<PhoneAccount>()
    private lateinit var framework: AndroidCallFramework
    private val handle = PhoneAccountHandle(
        ComponentName(application, ConnectionService::class.java),
        "org.openvoipalliance.androidphoneintegration",
    )

    @Before
    fun setUp() {
        mockPil()
        every { telecomManager.registerPhoneAccount(capture(account)) } returns Unit
        framework = AndroidCallFramework(application, calls, telecomManager)
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `a self-managed phone account is registered under the original APL id`() {
        assertEquals(handle, account.captured.accountHandle)
        assertEquals(
            PhoneAccount.CAPABILITY_SELF_MANAGED or PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS,
            account.captured.capabilities,
        )
    }

    @Test
    fun `calls are placed as audio-only calls on the phone account`() {
        val extras = slot<Bundle>()

        framework.placeCall("+31501234567")

        verify { telecomManager.placeCall(Uri.fromParts("tel", "+31501234567", null), capture(extras)) }
        assertFalse(extras.captured.getBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true))
        assertEquals(VideoProfile.STATE_AUDIO_ONLY, extras.captured.getInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, -1))
        assertEquals(handle, extras.captured.getParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE))
    }

    @Test
    fun `incoming calls are added with the caller's number`() {
        val extras = slot<Bundle>()

        framework.addNewIncomingCall("+31501234567")

        verify { telecomManager.addNewIncomingCall(handle, capture(extras)) }
        assertEquals(
            Uri.fromParts("tel", "+31501234567", null),
            extras.captured.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS),
        )
        assertEquals(handle, extras.captured.getParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE))
    }

    @Test
    fun `being in another call needs the phone state permission`() {
        every { telecomManager.isInCall } returns true

        shadowOf(application).denyPermissions(Manifest.permission.READ_PHONE_STATE)
        assertFalse(framework.isInCall)

        shadowOf(application).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        assertTrue(framework.isInCall)
    }

    @Test
    fun `telecom decides whether calls can be made and received`() {
        every { telecomManager.isOutgoingCallPermitted(handle) } returns true
        every { telecomManager.isIncomingCallPermitted(handle) } returns false

        assertTrue(framework.canMakeOutgoingCall)
        assertFalse(framework.canHandleIncomingCall)
    }

    @Test
    fun `pruning without calls disconnects and forgets the connection`() {
        val connection = mockk<Connection>(relaxed = true)
        val cause = slot<DisconnectCause>()
        framework.connection = connection

        framework.prune()

        verify {
            connection.setDisconnected(capture(cause))
            connection.destroy()
        }
        assertEquals(DisconnectCause.UNKNOWN, cause.captured.code)
        assertNull(framework.connection)
    }

    @Test
    fun `pruning during a call keeps the connection`() {
        val connection = mockk<Connection>(relaxed = true)
        framework.connection = connection
        calls.add(voipLibCall())

        framework.prune()

        assertSame(connection, framework.connection)
        verify(exactly = 0) { connection.destroy() }
    }
}
