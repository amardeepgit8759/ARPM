package com.placefy.application.port.in;

import com.placefy.domain.user.UserId;

/**
 * Hands a user everything held about them.
 *
 * <p>Takes a {@link UserId} and nothing else, so there is no parameter a caller could substitute
 * to export somebody else. The id comes from the verified token's subject.
 */
public interface ExportMyData {

    UserDataExport handle(UserId userId);
}
