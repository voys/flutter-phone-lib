package org.openvoipalliance.androidphoneintegration.telecom

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.telecom.ConnectionRequest
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.call.CallArrayList
import org.openvoipalliance.androidphoneintegration.call.Calls
import org.openvoipalliance.androidphoneintegration.clearPilInstance
import org.openvoipalliance.androidphoneintegration.events.Event.CallSetupFailedEvent
import org.openvoipalliance.androidphoneintegration.events.Event.CallSetupFailedEvent.IncomingCallSetupFailed
import org.openvoipalliance.androidphoneintegration.events.Event.CallSetupFailedEvent.OutgoingCallSetupFailed
import org.openvoipalliance.androidphoneintegration.events.EventsManager
import org.openvoipalliance.androidphoneintegration.installKoin
import org.openvoipalliance.androidphoneintegration.mockPil
import org.openvoipalliance.androidphoneintegration.pilCall
import org.openvoipalliance.voiplib.VoIPLib
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.telecom.Connection as AndroidConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConnectionServiceTest {
    private val pil = mockPil()
    private val voipLib = mockk<VoIPLib>(relaxed = true)
    private val events = mockk<EventsManager>(relaxed = true)
    private val androidCallFramework = mockk<AndroidCallFramework>()
    private var currentConnection: Connection? = null
    private val handle = PhoneAccountHandle(ComponentName(application, ConnectionService::class.java), "id")
    private val request = ConnectionRequest(handle, Uri.fromParts("tel", "+31501234567", null), Bundle())
    private lateinit var service: ConnectionService

    @Before
    fun setUp() {
        installKoin {
            single { pil }
            single { voipLib }
            single { androidCallFramework }
            factory { Connection(pil, voipLib, mockk(relaxed = true), Calls(mockk(), CallArrayList(Calls.MAX_CALLS))) }
        }
        every { pil.events } returns events
        every { pil.calls.active } returns pilCall(displayName = "Alice")
        every { androidCallFramework.connection } answers { currentConnection }
        every { androidCallFramework.connection = any() } answers { currentConnection = firstArg() }
        service = Robolectric.buildService(ConnectionService::class.java).get()
    }

    @After
    fun tearDown() = clearPilInstance()

    @Test
    fun `an outgoing connection is a self-managed VoIP call that dials the number`() {
        val connection = service.onCreateOutgoingConnection(handle, request)

        assertSame(currentConnection, connection)
        assertEquals(AndroidConnection.PROPERTY_SELF_MANAGED, connection.connectionProperties)
        assertEquals(
            AndroidConnection.CAPABILITY_HOLD or AndroidConnection.CAPABILITY_SUPPORT_HOLD or AndroidConnection.CAPABILITY_MUTE,
            connection.connectionCapabilities,
        )
        assertTrue(connection.audioModeIsVoip)
        assertEquals(request.address, connection.address)
        assertEquals(TelecomManager.PRESENTATION_ALLOWED, connection.addressPresentation)
        assertEquals("Alice", connection.callerDisplayName)
        verify { voipLib.callTo("+31501234567") }
    }

    @Test
    fun `an incoming connection is set up without dialing`() {
        val connection = service.onCreateIncomingConnection(handle, request)

        assertSame(currentConnection, connection)
        assertEquals(request.address, connection.address)
        verify(exactly = 0) { voipLib.callTo(any()) }
    }

    @Test
    fun `an existing connection is reused`() {
        val first = service.onCreateIncomingConnection(handle, request)

        assertSame(first, service.onCreateOutgoingConnection(handle, request))

        currentConnection = null
        assertNotSame(first, service.onCreateIncomingConnection(handle, request))
    }

    @Test
    fun `connections telecom refuses are reported as rejected`() {
        val outgoing = slot<OutgoingCallSetupFailed>()
        val incoming = slot<IncomingCallSetupFailed>()

        service.onCreateOutgoingConnectionFailed(handle, request)
        service.onCreateIncomingConnectionFailed(handle, request)

        verify {
            events.broadcast(capture(outgoing))
            events.broadcast(capture(incoming))
        }
        assertEquals(CallSetupFailedEvent.Reason.REJECTED_BY_ANDROID_TELECOM_FRAMEWORK, outgoing.captured.reason)
        assertEquals(CallSetupFailedEvent.Reason.REJECTED_BY_ANDROID_TELECOM_FRAMEWORK, incoming.captured.reason)
    }
}
