package com.soheil.lifeos;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID="soheil_tasks";
    @Override public void onReceive(Context context,Intent intent){
        String title=intent.getStringExtra("title"); if(title==null||title.trim().isEmpty())title="یک کار در SOHEIL منتظر توست";
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel c=new NotificationChannel(CHANNEL_ID,"SOHEIL Reminders",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Task and life reminders from SOHEIL");nm.createNotificationChannel(c);}
        Intent open=new Intent(context,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b=Build.VERSION.SDK_INT>=Build.VERSION_CODES.O?new android.app.Notification.Builder(context,CHANNEL_ID):new android.app.Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("SOHEIL • Reminder").setContentText(title).setStyle(new android.app.Notification.BigTextStyle().bigText(title)).setAutoCancel(true).setContentIntent(pi).setPriority(android.app.Notification.PRIORITY_HIGH);
        nm.notify((int)(System.currentTimeMillis()%Integer.MAX_VALUE),b.build());
    }
}
