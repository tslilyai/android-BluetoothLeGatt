/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package com.microsoft.embeddedsocial.server.model.notification;

import android.util.Log;

import com.microsoft.embeddedsocial.autorest.models.FeedResponseActivityView;
import com.microsoft.embeddedsocial.server.model.FeedUserResponse;
import com.microsoft.embeddedsocial.server.model.ListResponse;
import com.microsoft.embeddedsocial.server.model.view.ActivityView;

import org.mpisws.sddrservice.lib.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GetNotificationFeedResponse extends FeedUserResponse implements ListResponse<ActivityView> {

	private List<ActivityView> activities;
	private String deliveredActivityHandle;

	public GetNotificationFeedResponse(List<ActivityView> activities) {
		this.activities = activities;
		this.deliveredActivityHandle = activities.get(0).getHandle();
	}

	public GetNotificationFeedResponse (FeedResponseActivityView response) {
		activities = new ArrayList<>();
		for (com.microsoft.embeddedsocial.autorest.models.ActivityView view : response.getData()) {
			activities.add(new ActivityView(view));
		}
		this.deliveredActivityHandle = (activities.isEmpty() ? "" : activities.get(0).getHandle());
		setContinuationKey(response.getCursor());
	}

	@Override
	public List<ActivityView> getData() {
		return activities != null ? activities : Collections.emptyList();
	}

	public String getDeliveredActivityHandle() {
		return deliveredActivityHandle;
	}
}
