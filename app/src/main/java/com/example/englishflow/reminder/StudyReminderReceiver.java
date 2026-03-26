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

import android.content.ContentResolver;
import android.media.AudioAttributes;
import android.graphics.BitmapFactory;
import android.net.Uri;
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

        Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.nhac_chuong);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, StudyReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.english_flow))
                .setContentTitle("Đến giờ học rồi! 🚀")
                .setContentText("Mở EnglishFlow ngay để chinh phục mục tiêu hôm nay!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(soundUri)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(context, R.color.ef_primary))
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

        Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.nhac_chuong);
        
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();

        NotificationChannel newChannel = new NotificationChannel(
                StudyReminderScheduler.CHANNEL_ID,
                StudyReminderScheduler.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
        );
        newChannel.setDescription("Thong bao den gio hoc tap voi nhac chuong");
        newChannel.setSound(soundUri, audioAttributes);
        newChannel.enableVibration(true);
        notificationManager.createNotificationChannel(newChannel);
    }
}
