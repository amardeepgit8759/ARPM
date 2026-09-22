package com.placefy.application.fake;

import com.placefy.application.port.out.RefreshTokenGenerator;

/** Replaces the CSPRNG with a counter so tests can name the exact token a call produced. */
public class SequentialRefreshTokenGenerator implements RefreshTokenGenerator {

    private int next = 1;

    @Override
    public String generate() {
        return "refresh-token-" + next++;
    }

    public String lastIssued() {
        return "refresh-token-" + (next - 1);
    }
}
