package com.example.englishflow.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.example.englishflow.data.AppRepository;

import java.util.Calendar;

public final class StudyReminderScheduler {

    public static final String CHANNEL_ID = "study_reminder_channel_v3";
    public static final String CHANNEL_NAME = "Nhac nho hoc tap";
    public static final int NOTIFICATION_ID = 1001;
    private static final int REQUEST_CODE_REMINDER = 5001;

    private StudyReminderScheduler() {
    }

    public static void scheduleDailyReminder(Context context, int hour, int minute) {
        Context appContext = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = buildReminderPendingIntent(appContext);
        alarmManager.cancel(pendingIntent);

        Calendar triggerTime = Calendar.getInstance();
        triggerTime.set(Calendar.HOUR_OF_DAY, hour);
        triggerTime.set(Calendar.MINUTE, minute);
        triggerTime.set(Calendar.SECOND, 0);
        triggerTime.set(Calendar.MILLISECOND, 0);

        if (triggerTime.getTimeInMillis() <= System.currentTimeMillis()) {
            triggerTime.add(Calendar.DAY_OF_MONTH, 1);
        }

        long triggerAtMillis = triggerTime.getTimeInMillis();
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
    }

    public static void rescheduleFromPreferences(Context context) {
        AppRepository repository = AppRepository.getInstance(context.getApplicationContext());
        scheduleDailyReminder(context, repository.getReminderHour(), repository.getReminderMinute());
    }

    static PendingIntent buildReminderPendingIntent(Context context) {
        Intent intent = new Intent(context, StudyReminderReceiver.class);
        return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REMINDER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
