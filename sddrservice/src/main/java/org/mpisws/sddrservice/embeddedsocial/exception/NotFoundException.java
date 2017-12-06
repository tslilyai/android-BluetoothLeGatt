/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package org.mpisws.sddrservice.embeddedsocial.exception;

/**
 * Server exceptions in case resource not found
 */
public class NotFoundException extends StatusException {
	public static final int STATUS_CODE = 404;

	public NotFoundException(String message) {
		super(STATUS_CODE, message);
	}

	public NotFoundException(String message, Throwable cause) {
		super(STATUS_CODE, message, cause);
	}

	public NotFoundException(Throwable cause) {
		super(STATUS_CODE, cause);
	}

}