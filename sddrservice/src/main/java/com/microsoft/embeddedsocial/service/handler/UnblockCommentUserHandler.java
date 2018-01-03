/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package com.microsoft.embeddedsocial.service.handler;

import android.content.Intent;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.actions.Action;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.base.utils.debug.DebugLog;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.IContentService;
import com.microsoft.embeddedsocial.server.exception.NetworkRequestException;
import com.microsoft.embeddedsocial.server.model.content.comments.GetCommentRequest;
import com.microsoft.embeddedsocial.server.model.content.comments.GetCommentResponse;
import com.microsoft.embeddedsocial.service.IntentExtras;
import com.microsoft.embeddedsocial.service.ServiceAction;

/**
 * Get single comment.
 */
public class UnblockCommentUserHandler extends ActionHandler {
	@Override
	protected void handleAction(Action action, ServiceAction serviceAction, Intent intent) {
		IContentService contentService
				= GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();
		final String commentHandle = intent.getExtras().getString(IntentExtras.COMMENT_HANDLE);

		try {
			final GetCommentRequest request = new GetCommentRequest(commentHandle);
			GetCommentResponse response = contentService.getComment(request);
			UserAccount.getInstance().unblockUser(response.getComment().getUser().getHandle());
			Log.d("UserAccount", "Unblocked User");
		} catch (NetworkRequestException e) {
			DebugLog.logException(e);
			action.fail(e.getMessage());
		}
	}
}
