/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package com.microsoft.embeddedsocial.service.handler;

import android.content.Intent;

import com.microsoft.embeddedsocial.actions.Action;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.base.event.EventBus;
import com.microsoft.embeddedsocial.base.utils.debug.DebugLog;
import com.microsoft.embeddedsocial.event.content.GetCommentEvent;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.IContentService;
import com.microsoft.embeddedsocial.server.exception.NetworkRequestException;
import com.microsoft.embeddedsocial.server.model.content.comments.GetCommentRequest;
import com.microsoft.embeddedsocial.server.model.content.comments.GetCommentResponse;
import com.microsoft.embeddedsocial.service.IntentExtras;
import com.microsoft.embeddedsocial.service.ServiceAction;

import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.lib.Identifier;

/**
 * Get single comment.
 */
public class GetCommentHandler extends ActionHandler {
	@Override
	protected void handleAction(Action action, ServiceAction serviceAction, Intent intent) {
		IContentService contentService
				= GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();
		ESNotifs.NotificationCallback notifCallback = null;
		String parentText = null;
		final String commentHandle = intent.getExtras().getString(IntentExtras.COMMENT_HANDLE);
		if (intent.hasExtra(IntentExtras.NOTIF_CALLBACK)) {
			notifCallback = (ESNotifs.NotificationCallback) intent.getExtras().get(IntentExtras.NOTIF_CALLBACK);
			parentText = intent.getExtras().getString(IntentExtras.PARENT_TEXT);
		}

		try {
			final GetCommentRequest request = new GetCommentRequest(commentHandle);
			GetCommentResponse response = contentService.getComment(request);
			if (notifCallback != null) {
				notifCallback.onReceiveNotif(new ESNotifs.Notif(new Identifier(parentText.getBytes()), response.getComment().getCommentText(), response.getComment().getElapsedSeconds()));
			}
			EventBus.post(new GetCommentEvent(response.getComment(), response.getComment() != null));
		} catch (NetworkRequestException e) {
			DebugLog.logException(e);
			action.fail(e.getMessage());
			EventBus.post(new GetCommentEvent(null, false));
		}
	}
}
