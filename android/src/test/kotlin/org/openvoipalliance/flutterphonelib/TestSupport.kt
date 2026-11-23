package org.openvoipalliance.flutterphonelib

import org.openvoipalliance.flutterphonelib.configuration.PasswordStore
import android.net.Uri
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.common.StandardMethodCodec
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.openvoipalliance.androidphoneintegration.CallSessionState
import org.openvoipalliance.androidphoneintegration.audio.AudioRoute
import org.openvoipalliance.androidphoneintegration.audio.AudioState
import org.openvoipalliance.androidphoneintegration.audio.BluetoothAudioRoute
import org.openvoipalliance.androidphoneintegration.call.Call
import org.openvoipalliance.androidphoneintegration.call.CallDirection
import org.openvoipalliance.androidphoneintegration.call.CallState
import org.openvoipalliance.androidphoneintegration.contacts.Contact
import java.io.File
import java.nio.ByteBuffer

/** The JSON contract in `test/contract`, shared with the Dart and iOS tests. */
@Suppress("UNCHECKED_CAST")
object Contract {
    private val dir = File(
        System.getProperty("contractDir") ?: error("The contractDir system property is not set")
    )

    val androidToDart get() = read("android_to_dart.json") as Map<String, Any?>

    val dartToNative get() = read("dart_to_native.json") as List<Map<String, Any?>>

    private fun read(name: String) = fromJson(JSONTokener(File(dir, name).readText()).nextValue())

    private fun fromJson(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { fromJson(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { fromJson(value.get(it)) }
        else -> value
    }
}

/** The native session state that `android_to_dart.json` describes. */
object Fixtures {
    fun activeCall() = Call(
        remoteNumber = "+31501234567",
        displayName = "Alice",
        state = CallState.CONNECTED,
        direction = CallDirection.OUTBOUND,
        duration = 75,
        isOnHold = false,
        uuid = "c0ffee00-0000-4000-8000-000000000001",
        mos = 4.5f,
        currentMos = 4.25f,
        contact = Contact(
            "Alice Example",
            Uri.parse("content://com.android.contacts/contacts/1/photo"),
        ),
        callId = "call-id-1",
        reason = "",
    )

    fun inactiveCall() = Call(
        remoteNumber = "+31507654321",
        displayName = "",
        state = CallState.HELD_BY_LOCAL,
        direction = CallDirection.INBOUND,
        duration = 3725,
        isOnHold = true,
        uuid = "c0ffee00-0000-4000-8000-000000000002",
        mos = 0f,
        currentMos = 0f,
        contact = null,
        callId = "call-id-2",
        reason = "Busy Here",
    )

    fun sessionState() = CallSessionState(
        activeCall = activeCall(),
        inactiveCall = inactiveCall(),
        audioState = AudioState(
            currentRoute = AudioRoute.BLUETOOTH,
            availableRoutes = arrayOf(AudioRoute.SPEAKER, AudioRoute.PHONE, AudioRoute.BLUETOOTH),
            bluetoothDeviceName = "Headset",
            isMicrophoneMuted = true,
            bluetoothRoutes = arrayOf(BluetoothAudioRoute("Headset", "00:11:22:33:44:55")),
        ),
    )

    fun idleSessionState() = CallSessionState(
        activeCall = null,
        inactiveCall = null,
        audioState = AudioState(
            currentRoute = AudioRoute.PHONE,
            availableRoutes = arrayOf(AudioRoute.SPEAKER, AudioRoute.PHONE),
            bluetoothDeviceName = null,
            isMicrophoneMuted = false,
            bluetoothRoutes = emptyArray(),
        ),
    )
}

/** What the other side receives after [value] crosses the method channel. */
fun overTheWire(value: Any?): Any? {
    val buffer = StandardMessageCodec.INSTANCE.encodeMessage(value)!!
    buffer.rewind()
    return StandardMessageCodec.INSTANCE.decodeMessage(buffer)
}

fun methodCallOverTheWire(method: String, arguments: Any?): MethodCall {
    val buffer = StandardMethodCodec.INSTANCE.encodeMethodCall(MethodCall(method, arguments))
    buffer.rewind()
    return StandardMethodCodec.INSTANCE.decodeMethodCall(buffer)
}

/** JSON doesn't tell 4.5f from 4.5 or 75 from 75.0, so numbers compare as doubles. */
fun normalized(value: Any?): Any? = when (value) {
    is Map<*, *> -> value.entries.associate { it.key to normalized(it.value) }
    is List<*> -> value.map(::normalized)
    is Number -> value.toDouble()
    else -> value
}

/** Records what native sends to Dart. */
class RecordingMessenger : BinaryMessenger {
    val sent = mutableListOf<MethodCall>()

    override fun send(channel: String, message: ByteBuffer?) = send(channel, message, null)

    override fun send(channel: String, message: ByteBuffer?, callback: BinaryMessenger.BinaryReply?) {
        message!!.rewind()
        sent += StandardMethodCodec.INSTANCE.decodeMethodCall(message)
    }

    override fun setMessageHandler(channel: String, handler: BinaryMessenger.BinaryMessageHandler?) {}

    fun channel() = MethodChannel(this, "org.openvoipalliance.flutterphonelib/foreground")
}

sealed class Outcome {
    data class Success(val value: Any?) : Outcome()
    data class Error(val code: String, val message: String?) : Outcome()
    object NotImplemented : Outcome()
    object NoReply : Outcome()
}

class RecordingResult : MethodChannel.Result {
    val replies = mutableListOf<Outcome>()

    val outcome: Outcome
        get() = when (replies.size) {
            0 -> Outcome.NoReply
            1 -> replies.single()
            else -> throw AssertionError("Replied more than once: $replies")
        }

    override fun success(result: Any?) {
        replies += Outcome.Success(result)
    }

    override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
        replies += Outcome.Error(errorCode, errorMessage)
    }

    override fun notImplemented() {
        replies += Outcome.NotImplemented
    }
}

class FakePasswordStore(var password: String? = null) : PasswordStore {
    var failure: Exception? = null

    override fun read(): String? {
        failure?.let { throw it }
        return password
    }

    override fun write(password: String) {
        failure?.let { throw it }
        this.password = password
    }

    override fun delete() {
        failure?.let { throw it }
        password = null
    }
}
