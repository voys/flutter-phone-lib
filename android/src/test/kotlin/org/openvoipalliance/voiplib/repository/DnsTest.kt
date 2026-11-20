package org.openvoipalliance.voiplib.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DnsTest {
    private val publicServers = arrayOf("1.1.1.1", "8.8.8.8", "94.247.43.254", "9.9.9.9")
    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private val context = mockk<Context> {
        every { getSystemService(ConnectivityManager::class.java) } returns connectivityManager
    }

    @Test
    fun `public servers come before the local ones`() {
        every { connectivityManager.getLinkProperties(any()) } returns mockk<LinkProperties> {
            every { dnsServers } returns listOf(
                InetAddress.getByName("192.168.1.1"),
                InetAddress.getByName("fd00::1"),
            )
        }

        assertArrayEquals(
            publicServers + arrayOf("192.168.1.1", "fd00:0:0:0:0:0:0:1"),
            Dns(context).getServers(),
        )
    }

    @Test
    fun `only public servers are used without an active network`() {
        every { connectivityManager.getLinkProperties(any()) } returns null

        assertArrayEquals(publicServers, Dns(context).getServers())
    }

    @Test
    fun `only public servers are used when the local lookup fails`() {
        every { connectivityManager.getLinkProperties(any()) } throws SecurityException()

        assertArrayEquals(publicServers, Dns(context).getServers())
    }
}
