package org.openvoipalliance.androidphoneintegration

import android.app.Activity
import android.app.Application
import io.mockk.every
import io.mockk.mockk
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.linphone.core.Factory
import org.openvoipalliance.androidphoneintegration.call.Call
import org.openvoipalliance.androidphoneintegration.call.CallDirection
import org.openvoipalliance.androidphoneintegration.call.CallState
import org.openvoipalliance.androidphoneintegration.configuration.ApplicationSetup
import org.openvoipalliance.androidphoneintegration.contacts.Contact
import org.openvoipalliance.androidphoneintegration.di.di
import org.openvoipalliance.androidphoneintegration.di.pilModule
import org.robolectric.RuntimeEnvironment
import org.openvoipalliance.voiplib.model.Call as VoipLibCall

class TestCallActivity : Activity()

class TestIncomingCallActivity : Activity()

/** Replaces the PIL's dependency graph, so each class can be tested against mocks. */
internal fun installKoin(definitions: Module.() -> Unit) {
    di = koinApplication { modules(module { definitions() }) }
}

/** Koin 2 loads a module into one application only, so the real graph needs this to start again. */
internal fun allowRealPilModuleToLoad() {
    Module::class.java.getDeclaredField("isLoaded").apply { isAccessible = true }.set(pilModule, false)
}

/** `PIL.instance` is a lateinit with no way to unset it, but every test needs a fresh one. */
internal fun clearPilInstance() {
    PIL::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
}

/** linphone's own factory loads native libraries, which aren't available on the JVM. */
internal fun mockLinphoneFactory(): Factory = mockk<Factory>(relaxed = true).also(::setLinphoneFactory)

internal fun restoreLinphoneFactory() = setLinphoneFactory(null)

private fun setLinphoneFactory(factory: Factory?) {
    Factory::class.java.getDeclaredField("_Factory").apply { isAccessible = true }.set(null, factory)
}

internal fun applicationSetup(
    automaticallyLaunchCallActivity: ApplicationSetup.AutomaticallyLaunchCallActivity =
        ApplicationSetup.AutomaticallyLaunchCallActivity.ALWAYS,
    notifyOnMissedCall: Boolean = true,
) = ApplicationSetup(
    application = RuntimeEnvironment.getApplication(),
    activities = ApplicationSetup.Activities(
        TestCallActivity::class.java,
        TestIncomingCallActivity::class.java,
    ),
    automaticallyLaunchCallActivity = automaticallyLaunchCallActivity,
    notifyOnMissedCall = notifyOnMissedCall,
)

/** A relaxed PIL that is also `PIL.instance`, which is how most of the library reaches it. */
internal fun mockPil(app: ApplicationSetup = applicationSetup()): PIL =
    mockk<PIL>(relaxed = true).also {
        every { it.app } returns app
        PIL.instance = it
    }

internal val application: Application get() = RuntimeEnvironment.getApplication()

/** Each wraps its own mock, so each has its own identifier. */
internal fun voipLibCall(): VoipLibCall = VoipLibCall(mockk(relaxed = true))

internal fun pilCall(
    remoteNumber: String = "+31501234567",
    displayName: String = "",
    state: CallState = CallState.CONNECTED,
    direction: CallDirection = CallDirection.OUTBOUND,
    duration: Int = 0,
    isOnHold: Boolean = false,
    contact: Contact? = null,
) = Call(
    remoteNumber = remoteNumber,
    displayName = displayName,
    state = state,
    direction = direction,
    duration = duration,
    isOnHold = isOnHold,
    uuid = "c0ffee00-0000-4000-8000-000000000001",
    mos = 0f,
    currentMos = 0f,
    contact = contact,
    callId = "call-id",
    reason = "",
)
