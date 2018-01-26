/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package com.microsoft.embeddedsocial.service.handler;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.actions.Action;
import com.microsoft.embeddedsocial.actions.ActionsLauncher;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.base.utils.debug.DebugLog;
import com.microsoft.embeddedsocial.data.model.AccountData;
import com.microsoft.embeddedsocial.data.model.CreateAccountData;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.IAccountService;
import com.microsoft.embeddedsocial.server.IAuthenticationService;
import com.microsoft.embeddedsocial.server.exception.NetworkRequestException;
import com.microsoft.embeddedsocial.server.exception.NotFoundException;
import com.microsoft.embeddedsocial.server.model.UserRequest;
import com.microsoft.embeddedsocial.server.model.account.GetMyProfileRequest;
import com.microsoft.embeddedsocial.server.model.account.GetUserAccountRequest;
import com.microsoft.embeddedsocial.server.model.account.GetUserAccountResponse;
import com.microsoft.embeddedsocial.server.model.account.GetUserProfileResponse;
import com.microsoft.embeddedsocial.server.model.auth.AuthenticationResponse;
import com.microsoft.embeddedsocial.server.model.auth.CreateSessionRequest;
import com.microsoft.embeddedsocial.service.IntentExtras;
import com.microsoft.embeddedsocial.service.ServiceAction;
import com.microsoft.embeddedsocial.service.WorkerService;
import com.microsoft.embeddedsocial.ui.activity.CreateProfileActivity;
import com.microsoft.embeddedsocial.ui.util.SocialNetworkAccount;

import org.mpisws.sddrservice.R;

/**
 * Sends sign-in requests.
 */
public class SignInHandler extends ActionHandler {

	private final IAccountService accountService = GlobalObjectRegistry
			.getObject(EmbeddedSocialServiceProvider.class)
			.getAccountService();

	private final IAuthenticationService authenticationService = GlobalObjectRegistry
			.getObject(EmbeddedSocialServiceProvider.class)
			.getAuthenticationService();

	private final Context context;

	public SignInHandler(Context context) {
		this.context = context;
	}

	@Override
	protected void handleAction(Action action, ServiceAction serviceAction, Intent intent) {
		Log.d("SIGNIN", "Sign In Handler");
		signinWithThirdParty(action, intent.getParcelableExtra(IntentExtras.THIRD_PARTY_ACCOUNT));
		intent.removeExtra(IntentExtras.SOCIAL_NETWORK_ACCOUNT);
	}

	private void signinWithThirdParty(Action action, SocialNetworkAccount thirdPartyAccount) {
		CreateSessionRequest signInWithThirdPartyRequest = new CreateSessionRequest(
				thirdPartyAccount.getAccountType(),
				thirdPartyAccount.getThirdPartyAccessToken(),
				thirdPartyAccount.getThirdPartyRequestToken());


		String authorization = signInWithThirdPartyRequest.getAuthorization();
		GetMyProfileRequest getMyProfileRequest = new GetMyProfileRequest(authorization);

		try {
			// Determine the user's user handle
			GetUserProfileResponse getUserProfileResponse = getMyProfileRequest.send();

			// set the user handle and attempt sign in
			signInWithThirdPartyRequest.setRequestUserHandle(getUserProfileResponse.getUser().getHandle());
			AuthenticationResponse signInResponse = authenticationService.signInWithThirdParty(signInWithThirdPartyRequest);
			Log.d("SIGNIN", "Found my account");
			handleSuccessfulResult(action, signInResponse);
		} catch (NotFoundException e) {
			Log.d("SIGNIN", "Creating account");
             CreateAccountData createAccountData = new CreateAccountData.Builder()
                    .setIdentityProvider(thirdPartyAccount.getAccountType())
                    .setThirdPartyAccessToken(thirdPartyAccount.getThirdPartyAccessToken())
                    .setThirdPartyRequestToken(thirdPartyAccount.getThirdPartyRequestToken())
                    .build();
            thirdPartyAccount.clearTokens();
           ActionsLauncher.createAccount(context, createAccountData);
		} catch (Exception e) {
			DebugLog.logException(e);
			Log.d("SIGNIN", e.getMessage());
			UserAccount.getInstance().onSignInWithThirdPartyFailed();
		} finally {
			thirdPartyAccount.clearTokens();
		}
	}

	private void handleSuccessfulResult(Action action, AuthenticationResponse response)
			throws NetworkRequestException {

		String userHandle = response.getUserHandle();
		String sessionToken = UserRequest.createSessionAuthorization(response.getSessionToken());
		GetUserAccountRequest getUserRequest = new GetUserAccountRequest(sessionToken);
		GetUserAccountResponse userAccount = accountService.getUserAccount(getUserRequest);
		AccountData accountData = AccountData.fromServerResponse(userAccount.getUser());
		if (!action.isCompleted()) {
			int messageId = R.string.es_msg_general_signin_success;
			UserAccount.getInstance().onSignedIn(userHandle, sessionToken, accountData, messageId);
			WorkerService.getLauncher(context).launchService(ServiceAction.GCM_REGISTER);
		}
	}

	@Override
	public void dispose() {

	}
}
