/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package com.microsoft.embeddedsocial.data.model;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;

import com.microsoft.embeddedsocial.autorest.models.FollowerStatus;
import com.microsoft.embeddedsocial.autorest.models.IdentityProvider;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.image.ImageLocation;
import com.microsoft.embeddedsocial.server.model.view.ThirdPartyAccountView;
import com.microsoft.embeddedsocial.server.model.view.UserAccountView;
import com.microsoft.embeddedsocial.server.model.view.UserProfileView;

import org.mpisws.sddrservice.EncountersService;
import org.mpisws.sddrservice.encounters.SDDR_Core;
import org.mpisws.sddrservice.lib.Utils;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.mpisws.sddrservice.embeddedsocial.ESTopics.NUM_TOPICS_CREATED;
import static org.mpisws.sddrservice.embeddedsocial.ESTopics.NUM_TOPICS_TO_CREATE;
import static org.mpisws.sddrservice.lib.Utils.ack;

/**
 * Information about some user's account.
 */
public class AccountData implements Parcelable {
	private final static String TAG = AccountData.class.getSimpleName();

	private IdentityProvider identityProvider;
	private String firstName;
	private String lastName;
	private String userPhotoUrl;
	private String bio;
	private String thirdPartyAccountHandle;
	private String thirdPartyAccessToken;
	private long followersCount;
	private long followingCount;
	private boolean isPrivate;
	private FollowerStatus followedStatus = FollowerStatus.NONE;
	private Map<String, List<String>> unsentMsgs = new HashMap<>();
	private Set<String> pendingTopics = new ConcurrentSkipListSet<>();

	public AccountData() {
		identityProvider = IdentityProvider.MICROSOFT; // TODO verify this default value is OK
		unsentMsgs = new HashMap<>();
		pendingTopics = new ConcurrentSkipListSet<>();
	}

	/**
	 * Creates a new instance from a response of /GetUserProfile server method.
	 */
	public AccountData(UserProfileView userProfile) {
		this();
		this.firstName = userProfile.getFirstName();
		this.lastName = userProfile.getLastName();
		this.userPhotoUrl = userProfile.getUserPhotoUrl();
		this.bio = userProfile.getBio();
		this.followingCount = userProfile.getTotalFollowings();
		this.followersCount = userProfile.getTotalFollowers();
		this.isPrivate = userProfile.isPrivate();
		FollowerStatus status = userProfile.getFollowerStatus();
		this.followedStatus = status != null ? status : FollowerStatus.NONE;
	}

	private AccountData(Parcel in) {
		this.identityProvider = IdentityProvider.fromValue(in.readString());
		this.firstName = in.readString();
		this.lastName = in.readString();
		this.userPhotoUrl = in.readString();
		this.bio = in.readString();
		this.thirdPartyAccountHandle = in.readString();
		this.thirdPartyAccessToken = in.readString();
		this.followersCount = in.readLong();
		this.followingCount = in.readLong();
		this.isPrivate = in.readByte() != 0;
		FollowerStatus status = FollowerStatus.fromValue(in.readString());
		this.followedStatus = status != null ? status : FollowerStatus.NONE;
	}

	/**
	 * Whether we have rights to obtain the feeds of popular and recent user's topics.
	 */
	public boolean arePostsReadable() {
		return followedStatus != FollowerStatus.BLOCKED && (!isPrivate || followedStatus == FollowerStatus.FOLLOW);
	}

	public String getBio() {
		return bio;
	}

	public void setBio(String bio) {
		this.bio = bio;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getUserPhotoUrl() {
		return userPhotoUrl;
	}

	public ImageLocation getUserPhotoLocation() {
		return ImageLocation.createUserPhotoImageLocation(userPhotoUrl);
	}

	public void setUserPhotoUrl(String userPhotoUrl) {
		this.userPhotoUrl = userPhotoUrl;
	}

	public String getFullName() {
		return firstName + ' ' + lastName;
	}

	public IdentityProvider getIdentityProvider() {
		return identityProvider;
	}

	public void setIdentityProvider(IdentityProvider identityProvider) {
		this.identityProvider = identityProvider;
	}

	public String getThirdPartyAccountHandle() {
		return thirdPartyAccountHandle;
	}

	public void setThirdPartyAccountHandle(String thirdPartyAccountHandle) {
		this.thirdPartyAccountHandle = thirdPartyAccountHandle;
	}

	public String getThirdPartyAccessToken() {
		return thirdPartyAccessToken;
	}

	public void setThirdPartyAccessToken(String thirdPartyAccessToken) {
		this.thirdPartyAccessToken = thirdPartyAccessToken;
	}

	public long getFollowersCount() {
		return followersCount;
	}

	public void setFollowersCount(long followersCount) {
		this.followersCount = followersCount;
	}

	public long getFollowingCount() {
		return followingCount;
	}

	public void setFollowingCount(long followingCount) {
		this.followingCount = followingCount;
	}

	public boolean isPrivate() {
		return isPrivate;
	}

	public void setIsPrivate(boolean isPrivate) {
		this.isPrivate = isPrivate;
	}

	public FollowerStatus getFollowedStatus() {
		return followedStatus;
	}

	public void setFollowedStatus(FollowerStatus followedStatus) {
		this.followedStatus = followedStatus;
	}

	public void addUnsentMsg(String eid, String msg) {
		Log.v(TAG, "Adding " + msg + " unsent for " + eid);
		if (!unsentMsgs.containsKey(eid)) {
			this.unsentMsgs.put(eid, new LinkedList<>());
		}
		unsentMsgs.get(eid).add(msg);
	}

	public Map<String, List<String>> getUnsentMsgs() {
		return unsentMsgs;
	}

	public void addPendingTopic(String eid) {
		Log.v(TAG, "Adding pending topic " + eid);
		this.pendingTopics.add(eid);
	}

	public boolean pendingTopic(String eid) {
		return pendingTopics.contains(eid);
	}

	public void removePendingTopic(String eid) {
		Log.v(TAG, "Removing pending topic " + eid);
		pendingTopics.remove(eid);
        // TODO get rid of, just for topics test
		/*
		NUM_TOPICS_CREATED++;
        if (NUM_TOPICS_CREATED == NUM_TOPICS_TO_CREATE) {
            NUM_TOPICS_CREATED = 0;
            Log.d("TOPICS_TEST", System.currentTimeMillis() + ": Done creating topics!");
            for (int i = 0; i < 1000; i++)
                ack(BigInteger.valueOf(3), BigInteger.valueOf(3));
            Log.d("TOPICS_TEST", System.currentTimeMillis() + ": ACK!");
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			SDDR_Core.vibrate();
            Log.d("TOPICS_TEST", System.currentTimeMillis() + ": SPINNING ENDED");
        }*/
	}


	public void setAccountTypeFromThirdPartyAccounts(List<ThirdPartyAccountView> thirdPartyAccounts) {
		if (thirdPartyAccounts == null || thirdPartyAccounts.isEmpty()) {
			throw new IllegalArgumentException("Third party accounts list should not be null");
		}
		identityProvider = thirdPartyAccounts.get(0).getIdentityProvider();
	}

	/**
	 * Creates a new instance from a response of /GetUserAccount server method.
	 */
	public static AccountData fromServerResponse(UserAccountView userAccountView) {
		AccountData accountData = new AccountData();
		accountData.setFirstName(userAccountView.getFirstName());
		accountData.setLastName(userAccountView.getLastName());
		accountData.setUserPhotoUrl(userAccountView.getUserPhotoUrl());
		accountData.setBio(userAccountView.getBio());
		accountData.setIsPrivate(userAccountView.isPrivate());
		accountData.setAccountTypeFromThirdPartyAccounts(userAccountView.getThirdPartyAccounts());
		return accountData;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(this.identityProvider.toValue());
		dest.writeString(this.firstName);
		dest.writeString(this.lastName);
		dest.writeString(this.userPhotoUrl);
		dest.writeString(this.bio);
		dest.writeString(this.thirdPartyAccountHandle);
		dest.writeString(this.thirdPartyAccessToken);
		dest.writeLong(this.followersCount);
		dest.writeLong(this.followingCount);
		dest.writeByte(isPrivate ? (byte) 1 : (byte) 0);
		dest.writeString((this.followedStatus == null ? FollowerStatus.NONE : this.followedStatus).toValue());
	}

	public static final Creator<AccountData> CREATOR = new Creator<AccountData>() {
		public AccountData createFromParcel(Parcel source) {
			return new AccountData(source);
		}

		public AccountData[] newArray(int size) {
			return new AccountData[size];
		}
	};
}
