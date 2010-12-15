package com.cyanogenmod.android.fotakill;

import java.util.HashMap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class FOTANotifyReceiver extends BroadcastReceiver {
	static final String LOG_TAG = "FOTANotifyReceiver";

	static final int NOTIFY_ID = 1;

	static protected void checkNotification(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(FOTAShow.PREFS, Context.MODE_PRIVATE);
		if (!prefs.getBoolean(FOTAShow.PREFS_NOTIFY, false))
			return;

		HashMap<String,String> update = FOTAShow.queryUpdateStatus(context);
		String token = update.get(FOTAShow.NAME_UPDATE_TOKEN);
		if (token == null)
			return;

		if (token.equals(prefs.getString(FOTAShow.PREFS_LAST_NOTIFY_TOKEN, "")))
			return;

		NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		Intent fotaActivity = new Intent(context, FOTAShow.class);

		Notification n = new Notification(android.R.drawable.stat_notify_sync_noanim, context.getString(R.string.NotifyOTAAvailable), System.currentTimeMillis());
		n.flags = Notification.FLAG_AUTO_CANCEL;

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, fotaActivity, 0);
		n.setLatestEventInfo(context, context.getString(R.string.NotifyOTAAvailable), update.get(FOTAShow.NAME_UPDATE_TITLE), contentIntent);
		try {
			nm.notify(NOTIFY_ID, n);
		} catch (SecurityException e) {
			/* Ignore */
		}
	}

	static protected void clearNotification(Context context) {
		NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(NOTIFY_ID);
	}

	@Override
	public void onReceive(Context context, Intent changed_intent) {
		checkNotification(context);
	}
}
