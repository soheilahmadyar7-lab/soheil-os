package com.soheil.lifeos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Never places private task content into a lock-screen notification. */
public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID="soheil_private_reminders";
    @Override public void onReceive(Context context,Intent intent){
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            NotificationChannel c=new NotificationChannel(CHANNEL_ID,"SOHEIL Private Reminders",NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Privacy-preserving reminders from SOHEIL");
            c.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            nm.createNotificationChannel(c);
        }
        Intent open=new Intent(context,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new android.app.Notification.Builder(context,CHANNEL_ID):new android.app.Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("SOHEIL")
                .setContentText("یک یادآوری خصوصی منتظر توست")
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(android.app.Notification.PRIORITY_HIGH);
        nm.notify((int)(System.currentTimeMillis()%Integer.MAX_VALUE),b.build());
    }
}
