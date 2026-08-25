package com.example.agent.provider;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

public class TokenEstimator {
    private final Encoding encoding = Encodings.newDefaultEncodingRegistry()
        .getEncoding(EncodingType.CL100K_BASE);

    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return encoding.countTokens(text);
    }
}