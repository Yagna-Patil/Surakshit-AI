package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val pendingResult: PendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    if (messages != null) {
                        for (sms in messages) {
                            val sender = sms.displayOriginatingAddress ?: "Unknown"
                            val messageBody = sms.displayMessageBody ?: sms.messageBody ?: ""

                            if (messageBody.isBlank()) continue

                            // Analyze incoming real-time SMS
                            val analysis = ScamAnalyzer.analyze(
                                message = messageBody,
                                languageCode = "hi", // Default fallback for real-time notification
                                type = FraudType.SMS
                            )

                            // Emit directly to active UI state flow
                            SmsRepository.emitSms(sender, messageBody)

                            // Display instant system alert notification
                            showRealtimeNotification(context, sender, messageBody, analysis)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showRealtimeNotification(
        context: Context,
        sender: String,
        messageBody: String,
        analysis: AnalysisResult
    ) {
        val channelId = "surakshit_sms_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Surakshit Realtime Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications for financial scam detection"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SCAM_TEXT", messageBody)
            putExtra("SENDER", sender)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = if (analysis.riskLevel == RiskLevel.SAFE)
            android.R.drawable.stat_sys_download_done
        else
            android.R.drawable.stat_sys_warning

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle("🛡️ Surakshit AI: ${analysis.headline}")
            .setContentText("${analysis.explanation} (From: $sender)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${analysis.explanation}\n\n👉 ${analysis.advice}\n\nSMS: \"$messageBody\"")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), builder.build())
    }
}