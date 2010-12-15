package com.cyanogenmod.android.fotakill;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton;

public class FOTAShow extends Activity
	implements View.OnClickListener,
		View.OnLongClickListener,
		CompoundButton.OnCheckedChangeListener {

	static final String LOG_TAG = "FOTAShow";

	static final Uri GSERVICES_URI = Uri.parse("content://com.google.android.gsf.gservices/prefix");
	static final String NAME_UPDATE_URL = "update_url";
	static final String NAME_UPDATE_SIZE = "update_size";
	static final String NAME_UPDATE_TITLE = "update_title";
	static final String NAME_UPDATE_DESCRIPTION = "update_description";
	static final String NAME_UPDATE_URGENCY = "update_urgency";
	static final String NAME_UPDATE_TOKEN = "update_token";

	static final String PREFS = "default";
	static final String PREFS_NOTIFY = "fota_notify";
	static final String PREFS_LAST_NOTIFY_TOKEN = "fota_last_token";

	static protected HashMap<String,String> queryUpdateStatus(Context ctx) {
		ContentResolver cr = ctx.getContentResolver();

		final String[] keys = {
			NAME_UPDATE_SIZE,
			NAME_UPDATE_TITLE,
			NAME_UPDATE_DESCRIPTION,
			NAME_UPDATE_URGENCY,
			NAME_UPDATE_TOKEN
		};

		Cursor cursor = cr.query(GSERVICES_URI, null, null, keys, null);

		if (cursor != null && cursor.getCount() > 0) {
			try {
				int column_name = cursor.getColumnIndexOrThrow("key");
				int column_value = cursor.getColumnIndexOrThrow("value");

				HashMap<String,String> map = new HashMap<String,String>();

				for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
					String name = cursor.getString(column_name);
					String value = cursor.getString(column_value);
					map.put(name, value);
				}

				return map;
			} catch (IllegalArgumentException e) {
				/* from cursor.getColumnIndexOrThrow */
				Log.e(LOG_TAG, e.getMessage());
				return null;
			}
		} else {
			Log.e(LOG_TAG, "Empty GServices query " + (cursor!=null?cursor.toString() + " " + cursor.getCount():"<null>"));
			return new HashMap<String,String>();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstance) {
		super.onCreate(savedInstance);

		setContentView(R.layout.fotashow);

		View updateView = findViewById(R.id.UpdateView);
		TextView noUpdateText = (TextView) findViewById(R.id.NoUpdateView);

		SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		((CompoundButton) findViewById(R.id.OTAShow)).setChecked(prefs.getBoolean(PREFS_NOTIFY, false));

		HashMap<String,String> update = queryUpdateStatus(this);

		boolean updateAvailable = (update != null);
		String update_title = null;
		String update_description, update_urgency, update_size;

		if (updateAvailable) {
			update_title = update.get(NAME_UPDATE_TITLE);
			if (update_title == null)
				updateAvailable = false;
		}

		if (!updateAvailable) {
			if (update == null) {
				noUpdateText.setText(getString(R.string.GSFQueryError));
			} else {
				if (prefs.contains(PREFS_LAST_NOTIFY_TOKEN)) {
					SharedPreferences.Editor edit = prefs.edit();
					edit.remove(PREFS_LAST_NOTIFY_TOKEN);
					edit.commit();
				}
			}

			return;
		}

		noUpdateText.setVisibility(View.GONE);
		updateView.setVisibility(View.VISIBLE);

		update_description = update.get(NAME_UPDATE_DESCRIPTION);
		if (update_description == null) update_description = getString(R.string.OTANoDescription);
		update_urgency = update.get(NAME_UPDATE_URGENCY);
		if (update_urgency == null) update_urgency = getString(R.string.OTANoUrgency);
		update_size = update.get(NAME_UPDATE_SIZE);
		if (update_size == null) update_size = getString(R.string.OTANoSize);

		((TextView) findViewById(R.id.OTATitle)).setText(Html.fromHtml(update_title));
		((TextView) findViewById(R.id.OTADescription)).setText(Html.fromHtml(update_description));
		((TextView) findViewById(R.id.OTAUrgency)).setText(update_urgency);
		((TextView) findViewById(R.id.OTASize)).setText(update_size);

		((Button) findViewById(R.id.OTAUrlButton)).setOnClickListener(this);
		((TextView) findViewById(R.id.OTAUrl)).setOnLongClickListener(this);
		((CompoundButton) findViewById(R.id.OTAShow)).setOnCheckedChangeListener(this);

		setUpdateUrl(savedInstance != null ? savedInstance.getString("url") : null);

		String token = update.get(NAME_UPDATE_TOKEN);
		if (!token.equals(prefs.getString(PREFS_LAST_NOTIFY_TOKEN, ""))) {
			SharedPreferences.Editor edit = prefs.edit();
			edit.putString(PREFS_LAST_NOTIFY_TOKEN, token);
			edit.commit();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		final TextView text = (TextView) findViewById(R.id.OTAUrl);
		final String update_url = text.getText().toString();
		if (update_url != null)
			state.putString("url", update_url);
	}

	private class UrlThread extends AsyncTask<String, Void, Void> {
		@Override
		protected Void doInBackground(final String... args) {
			final String gservices_path = args[0];

			final String[] argv = {
				"su",
				"-c",
				"sqlite3 -batch " + gservices_path +" \"SELECT value FROM main WHERE name = 'update_url';\""
			};

			String update_url = null;

			try {
				Runtime rt = Runtime.getRuntime();
				Process p = rt.exec(argv);
				InputStreamReader stdoutReader = new InputStreamReader(p.getInputStream());
				BufferedReader stdout = new BufferedReader(stdoutReader);
				int exitStatus = p.waitFor();
				if (exitStatus == 0) {
					update_url = stdout.readLine();
					Log.v(LOG_TAG, "Got update URL: " + update_url);
				} else {
					Log.e(LOG_TAG, "sqlite3 error getting URL: " + exitStatus);
				}
			} catch (IOException e) {
				Log.e(LOG_TAG, "Error executing sqlite3 for URL");
			} catch (InterruptedException e) {
				Log.e(LOG_TAG, "Timeout getting URL");
			} finally {
				setUpdateUrl(update_url);
			}

			return null;
		}
	}

	private UrlThread urlThread;

	/* Only call from UI thread! */
	private void findUpdateUrl() {
		final Button button = (Button) findViewById(R.id.OTAUrlButton);

		if (!button.isEnabled() || urlThread != null)
			return;

		button.setEnabled(false);

		String gservices_path;
		try {
			PackageManager pm = getPackageManager();
			ApplicationInfo ai = pm.getApplicationInfo("com.google.android.gsf", 0);
			gservices_path = ai.dataDir + "/databases/gservices.db";
		} catch (NameNotFoundException e) {
			Log.e(LOG_TAG, "Unable to find database path for GSF");
			button.setEnabled(true);
			return;
		}

		urlThread = new UrlThread();
		urlThread.execute(gservices_path);
	}

	private void setUpdateUrl(final String update_url) {
		final Button button = (Button) findViewById(R.id.OTAUrlButton);
		final TextView text = (TextView) findViewById(R.id.OTAUrl);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (update_url != null) {
					button.setVisibility(View.GONE);
					text.setText(update_url);
					text.setVisibility(View.VISIBLE);
				} else {
					button.setEnabled(true);
				}
				urlThread = null;
			}
		});
	}

	@Override
	public void onClick(View button) {
		assert(button.getId() == R.id.OTAUrlButton);

		findUpdateUrl();
	}

	@Override
	public boolean onLongClick(View v) {
		assert(v.getId() == R.id.OTAUrl);

		ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		if (cm == null) return false;

		TextView text = (TextView) v;
		cm.setText(text.getText());

		Toast.makeText(this, "Copied URL to clipboard", Toast.LENGTH_SHORT).show();

		return true;
	}

	@Override
	public void onCheckedChanged(CompoundButton show, boolean state) {
		assert(show.getId() == R.id.OTAShow);

		SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		SharedPreferences.Editor edit = prefs.edit();
		edit.putBoolean(PREFS_NOTIFY, state);
		edit.commit();

		if (state) {
			FOTANotifyReceiver.checkNotification(this);
		} else {
			FOTANotifyReceiver.clearNotification(this);
		}
	}
}
