/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package com.microsoft.embeddedsocial.service.handler;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.actions.Action;
import com.microsoft.embeddedsocial.actions.ActionsLauncher;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.base.utils.debug.DebugLog;
import com.microsoft.embeddedsocial.data.model.AccountData;
import com.microsoft.embeddedsocial.data.model.AccountDataDifference;
import com.microsoft.embeddedsocial.data.model.CreateAccountData;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.IAccountService;
import com.microsoft.embeddedsocial.server.IAuthenticationService;
import com.microsoft.embeddedsocial.server.exception.NetworkRequestException;
import com.microsoft.embeddedsocial.server.model.UserRequest;
import com.microsoft.embeddedsocial.server.model.account.CreateUserRequest;
import com.microsoft.embeddedsocial.server.model.account.DeleteUserRequest;
import com.microsoft.embeddedsocial.server.model.account.GetUserAccountRequest;
import com.microsoft.embeddedsocial.server.model.account.GetUserAccountResponse;
import com.microsoft.embeddedsocial.server.model.auth.AuthenticationResponse;
import com.microsoft.embeddedsocial.service.IntentExtras;
import com.microsoft.embeddedsocial.service.ServiceAction;
import com.microsoft.embeddedsocial.service.WorkerService;

import org.mpisws.sddrservice.R;

import java.io.IOException;

/**
 * Sends a create account request to the server.
 */
public class CreateAccountHandler extends ActionHandler {

    private final IAccountService accountService = GlobalObjectRegistry
            .getObject(EmbeddedSocialServiceProvider.class)
            .getAccountService();

    private final IAuthenticationService authenticationService = GlobalObjectRegistry
            .getObject(EmbeddedSocialServiceProvider.class)
            .getAuthenticationService();

    private final Context context;

    public CreateAccountHandler(Context context) {
        this.context = context;
        Log.v("UserAccount", "CreateAccountHandlerCreated");
    }

    @Override
    protected void handleAction(Action action, ServiceAction serviceAction, Intent intent) {
        Bundle extras = intent.getExtras();
        CreateAccountData createAccountData = extras.getParcelable(IntentExtras.CREATE_ACCOUNT_DATA);
        CreateUserRequest createUserRequest = new CreateUserRequest.Builder()
                .setFirstName("First_Name")
                .setLastName("Last_Name")
                .setInstanceId("SDDR_ID")
                .setIdentityProvider(createAccountData.getIdentityProvider())
                .setAccessToken(createAccountData.getThirdPartyAccessToken())
                .build();
        try {
            Log.v("UserAccount", "Creating account");
            AuthenticationResponse createUserResponse = accountService.createUser(createUserRequest);
            handleSuccessfulResult(action, createUserResponse);
        } catch (NetworkRequestException e) {
            Log.v("UserAccount", "Account creation failed " + e.getMessage());
            DebugLog.logException(e);
            UserAccount.getInstance().onCreateUserFailed();
            action.fail();
        }
    }

    private void handleSuccessfulResult(Action action, AuthenticationResponse response)
            throws NetworkRequestException {

        Log.v("UserAccount", "Account created");
        String userHandle = response.getUserHandle();
        String sessionToken = UserRequest.createSessionAuthorization(response.getSessionToken());
        GetUserAccountRequest getUserRequest = new GetUserAccountRequest(sessionToken);
        GetUserAccountResponse userAccount = accountService.getUserAccount(getUserRequest);
        AccountData accountData = AccountData.fromServerResponse(userAccount.getUser());
        if (!action.isCompleted()) {
            int messageId = R.string.es_msg_general_create_user_success;
            UserAccount.getInstance().onSignedIn(userHandle, sessionToken, accountData, messageId);
            //WorkerService.getLauncher(context).launchService(ServiceAction.GCM_REGISTER);
        }
    }

    @Override
    public void dispose() {

    }
}
