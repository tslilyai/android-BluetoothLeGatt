/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package com.microsoft.embeddedsocial.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Tracks network availability.
 */
public class NetworkAvailability extends BroadcastReceiver {

	private boolean networkAvailable = true;

	public void startMonitoring(Context context) {
		checkActiveNetwork(context);
	}

	private void checkActiveNetwork(Context context) {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		networkAvailable = activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	public boolean isNetworkAvailable() {
		return networkAvailable;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		checkActiveNetwork(context);
	}
}
