package com.example.englishflow.reminder;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.englishflow.MainActivity;
import com.example.englishflow.R;

public class StudyReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ensureChannel(context);

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, StudyReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
                .setContentTitle("Den gio hoc roi!")
                .setContentText("Mo EnglishFlow de tiep tuc bai hoc hom nay.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openAppPendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(StudyReminderScheduler.NOTIFICATION_ID, builder.build());
        }

        StudyReminderScheduler.rescheduleFromPreferences(context);
    }

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }

        NotificationChannel channel = notificationManager.getNotificationChannel(StudyReminderScheduler.CHANNEL_ID);
        if (channel != null) {
            return;
        }

        NotificationChannel newChannel = new NotificationChannel(
                StudyReminderScheduler.CHANNEL_ID,
                StudyReminderScheduler.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        newChannel.setDescription("Thong bao den gio hoc tap");
        notificationManager.createNotificationChannel(newChannel);
    }
}
