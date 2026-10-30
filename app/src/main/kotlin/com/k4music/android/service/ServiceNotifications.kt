package com.k4music.android.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.media3.common.util.NotificationUtil.Importance
import androidx.media3.common.util.UnstableApi
import com.k4music.app.R
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.random.Random
import kotlin.reflect.KProperty

abstract class NotificationChannels {
    private val handler = Handler(Looper.getMainLooper())

    private fun dispatch(onFailure: (Throwable) -> Unit = {}, action: () -> Unit): Result<Boolean> {
        fun execute() = runCatching(action).onFailure {
            android.util.Log.e("K4 Audio", "Notification operation failed", it)
            onFailure(it)
        }
        return if (Looper.myLooper() == Looper.getMainLooper()) execute().map { true }
        else runCatching { handler.post { execute() } }
    }

    @OptIn(UnstableApi::class)
    inner class Channel internal constructor(
        val id: String,
        @param:StringRes
        val description: Int,
        val notificationId: Int? = null,
        val importance: @Importance Int,
        val options: NotificationChannelCompat.Builder.() -> NotificationChannelCompat.Builder
    ) {
        private val Context.notificationManager
            get() = getSystemService<NotificationManager>()
                ?: error("No NotificationManager available")

        private fun createNotification(
            context: Context,
            notification: NotificationCompat.Builder.() -> NotificationCompat.Builder
        ): Pair<Int, Notification> =
            (notificationId ?: randomNotificationId()) to NotificationCompat.Builder(context, id)
                .let {
                    if (notificationId == null) it else it.setOnlyAlertOnce(false)
                }
                .run(notification)
                .build()

        fun upsertChannel(context: Context) = NotificationManagerCompat
            .from(context)
            .createNotificationChannel(
                NotificationChannelCompat.Builder(id, importance)
                    .setName(context.getString(description))
                    .run(options)
                    .build()
            )

        fun sendNotification(
            context: Context,
            notification: NotificationCompat.Builder.() -> NotificationCompat.Builder
        ) = dispatch {
                val manager = context.notificationManager
                upsertChannel(context)
                val [id, notif] = createNotification(context, notification)
                manager.notify(id, notif)
        }

        context(s: Service)
        fun startForeground(
            context: Context,
            notification: NotificationCompat.Builder.() -> NotificationCompat.Builder
        ) = dispatch(onFailure = { s.stopSelf() }) {
                upsertChannel(context)
                val [id, notif] = createNotification(context, notification)
                s.startForeground(id, notif)
        }

        fun cancel(
            context: Context,
            notificationId: Int? = null
        ) = dispatch {
            (this.notificationId ?: notificationId)?.let { context.notificationManager.cancel(it) }
        }
    }

    private val mutableChannels = mutableListOf<Channel>()
    private val index = AtomicInteger(1001)

    private fun randomNotificationId(): Int {
        var random = Random.nextInt().absoluteValue
        while (random in 1001..2001) {
            random = Random.nextInt().absoluteValue
        }
        return random
    }

    context(application: Application)
    fun createAll() = dispatch {
        mutableChannels.forEach { it.upsertChannel(application) }
    }

    @OptIn(UnstableApi::class)
    fun channel(
        name: String? = null,
        @StringRes
        description: Int,
        importance: @Importance Int,
        singleNotification: Boolean,
        options: NotificationChannelCompat.Builder.() -> NotificationChannelCompat.Builder = { this }
    ) = readOnlyProvider<NotificationChannels, Channel> { _, property ->
        val channel = Channel(
            id = "${name?.lowercase() ?: property.name.lowercase()}_channel_id",
            description = description,
            notificationId = if (singleNotification) index.getAndIncrement().also {
                if (it > 2001) error("More than 1000 unique notifications created!")
            } else null,
            importance = importance,
            options = options
        )
        mutableChannels += channel
        { _, _ -> channel }
    }
}

inline fun <ThisRef, Return> readOnlyProvider(
    crossinline provide: (
        thisRef: ThisRef,
        property: KProperty<*>
    ) -> (thisRef: ThisRef, property: KProperty<*>) -> Return
) = PropertyDelegateProvider<ThisRef, ReadOnlyProperty<ThisRef, Return>> { thisRef, property ->
    val provider = provide(thisRef, property)
    ReadOnlyProperty { innerThisRef, innerProperty -> provider(innerThisRef, innerProperty) }
}

object ServiceNotifications : NotificationChannels() {
    val default by channel(
        description = R.string.now_playing,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        singleNotification = true
    )

    val sleepTimer by channel(
        name = "sleep_timer",
        description = R.string.sleep_timer,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        singleNotification = true
    )

    val download by channel(
        description = R.string.pre_cache,
        importance = NotificationManagerCompat.IMPORTANCE_LOW,
        singleNotification = true
    )

    val version by channel(
        description = R.string.version_check,
        importance = NotificationManagerCompat.IMPORTANCE_HIGH,
        singleNotification = true
    ) {
        setLightsEnabled(true).setVibrationEnabled(true)
    }
}
