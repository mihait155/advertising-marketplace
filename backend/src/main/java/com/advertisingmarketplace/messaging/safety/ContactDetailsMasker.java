package com.advertisingmarketplace.messaging.safety;

import com.advertisingmarketplace.messaging.MessagingProperties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContactDetailsMasker {

    public static final String MASK = "[contact details hidden]";

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\w)[+(]?\\d[\\d ().-]{5,}\\d(?!\\w)");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DIGIT_GROUP = Pattern.compile("\\d+");
    private static final int MIN_PHONE_DIGITS = 7;
    private static final int MIN_SINGLE_RUN_DIGITS = 9;

    private final MessagingProperties properties;

    public String apply(String text) {
        if (!properties.maskContactDetails() || text == null) {
            return text;
        }
        return mask(text);
    }

    static String mask(String text) {
        String masked = EMAIL.matcher(text).replaceAll(MASK);
        Matcher phone = PHONE.matcher(masked);
        StringBuilder out = new StringBuilder();
        while (phone.find()) {
            phone.appendReplacement(out, Matcher.quoteReplacement(looksLikePhone(phone.group()) ? MASK
                    : phone.group()));
        }
        phone.appendTail(out);
        return out.toString();
    }

    private static boolean looksLikePhone(String candidate) {
        String trimmed = candidate.strip();
        if (ISO_DATE.matcher(trimmed).matches()) {
            return false;
        }
        long digits = trimmed.chars().filter(Character::isDigit).count();
        long groups = DIGIT_GROUP.matcher(trimmed).results().count();
        boolean prefixed = trimmed.startsWith("+") || trimmed.startsWith("(");
        return digits >= MIN_PHONE_DIGITS && (prefixed || groups >= 3)
                || groups == 1 && digits >= MIN_SINGLE_RUN_DIGITS;
    }
}
