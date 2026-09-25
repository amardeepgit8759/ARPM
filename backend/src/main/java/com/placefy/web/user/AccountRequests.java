package com.placefy.web.user;

/** Request bodies for the account endpoints. */
final class AccountRequests {

    private AccountRequests() {}

    /**
     * @param password the caller's current password, re-entered. Not length-checked here: this is
     *     verification of an existing credential, and applying today's minimum would lock out
     *     anyone whose password predates it.
     */
    record DeleteAccountRequest(String password) {}

    record ChangePasswordRequest(String currentPassword, String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[REDACTED]";
        }
    }
}
