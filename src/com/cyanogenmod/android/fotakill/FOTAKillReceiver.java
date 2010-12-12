package com.cyanogenmod.android.fotakill;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class FOTAKillReceiver extends android.content.BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent changed_intent) {
		// TODO Auto-generated method stub
		Log.v("FOTAKill", "Killing FOTA");
		Intent intent = new Intent("com.google.gservices.intent.action.GSERVICES_OVERRIDE");
		intent.putExtra("update_url", "");
		context.sendBroadcast(intent);
	}

}
