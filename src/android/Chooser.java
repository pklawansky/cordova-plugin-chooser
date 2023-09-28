package com.cyph.cordova;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.Exception;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Chooser extends CordovaPlugin {
	private static final String ACTION_OPEN = "getFile"; // Change the action name to "getFile"
	private static final int PICK_FILES_REQUEST = 2; // Change the request code to a unique value (e.g., 2)
	private static final String TAG = "Chooser";

	/** @see https://stackoverflow.com/a/17861016/459881 */
	public static byte[] getBytesFromInputStream(InputStream is) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] buffer = new byte[0xFFFF];

		for (int len = is.read(buffer); len != -1; len = is.read(buffer)) {
			os.write(buffer, 0, len);
		}

		return os.toByteArray();
	}

	/** @see https://stackoverflow.com/a/23270545/459881 */
	public static String getDisplayName(ContentResolver contentResolver, Uri uri) {
		String[] projection = { MediaStore.MediaColumns.DISPLAY_NAME };
		Cursor metaCursor = contentResolver.query(uri, projection, null, null, null);

		if (metaCursor != null) {
			try {
				if (metaCursor.moveToFirst()) {
					return metaCursor.getString(0);
				}
			} finally {
				metaCursor.close();
			}
		}

		return "File";
	}

	private CallbackContext callback;
	private Boolean includeData;

	public void chooseFiles(CallbackContext callbackContext, String accept, Boolean includeData) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		if (!accept.equals("*/*")) {
			intent.putExtra(Intent.EXTRA_MIME_TYPES, accept.split(","));
		}
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Allow multiple files
		intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
		this.includeData = includeData;

		Intent chooser = Intent.createChooser(intent, "Select Files");
		cordova.startActivityForResult(this, chooser, Chooser.PICK_FILES_REQUEST);

		PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
		pluginResult.setKeepCallback(true);
		this.callback = callbackContext;
		callbackContext.sendPluginResult(pluginResult);
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		try {
			if (action.equals(Chooser.ACTION_OPEN)) {
				this.chooseFiles(callbackContext, args.getString(0), args.getBoolean(1));
				return true;
			}
		} catch (JSONException err) {
			this.callback.error("Execute failed: " + err.toString());
		}
		return false;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		try {
			if (requestCode == Chooser.PICK_FILES_REQUEST && this.callback != null) {
				if (resultCode == Activity.RESULT_OK) {
					ClipData clipData = data.getClipData();
					if (clipData != null) {
						JSONArray results = new JSONArray();
						ContentResolver contentResolver = this.cordova.getActivity().getContentResolver();

						for (int i = 0; i < clipData.getItemCount(); i++) {
							Uri uri = clipData.getItemAt(i).getUri();
							String name = Chooser.getDisplayName(contentResolver, uri);
							String mediaType = contentResolver.getType(uri);
							if (mediaType == null || mediaType.isEmpty()) {
								mediaType = "application/octet-stream";
							}

							String base64 = "";
							if (this.includeData) {
								byte[] bytes = Chooser.getBytesFromInputStream(contentResolver.openInputStream(uri));
								base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
							}

							JSONObject result = new JSONObject();
							result.put("data", base64);
							result.put("mediaType", mediaType);
							result.put("name", name);
							result.put("uri", uri.toString());
							results.put(result);
						}

						this.callback.success(results);
					} else {
						this.callback.error("No files selected.");
					}
				} else if (resultCode == Activity.RESULT_CANCELED) {
					this.callback.success("RESULT_CANCELED");
				} else {
					this.callback.error(resultCode);
				}
			}
		} catch (Exception err) {
			this.callback.error("Failed to read files: " + err.toString());
		}
	}
}
