package org.openvoipalliance.androidphoneintegration.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.openvoipalliance.androidphoneintegration.PIL
import org.openvoipalliance.androidphoneintegration.application
import org.openvoipalliance.androidphoneintegration.installKoin
import org.openvoipalliance.androidphoneintegration.service.NotificationButtonReceiver
import org.robolectric.Shadows.shadowOf

internal val notificationManager: NotificationManager
    get() = application.getSystemService(NotificationManager::class.java)

/** What notifications inject: the PIL, the application and the system notification manager. */
internal fun installNotificationKoin(pil: PIL) = installKoin {
    single { pil }
    single<Context> { application }
    single { notificationManager }
}

internal fun postedNotification(id: Int): Notification =
    shadowOf(notificationManager).getNotification(id)

internal val PendingIntent.intent: Intent get() = shadowOf(this).savedIntent

internal fun assertButtonPress(action: NotificationButtonReceiver.Action, pendingIntent: PendingIntent?) {
    assertEquals(true, shadowOf(pendingIntent).isBroadcastIntent)
    assertEquals(ComponentName(application, NotificationButtonReceiver::class.java), pendingIntent!!.intent.component)
    assertEquals(action.name, pendingIntent.intent.action)
}
