package com.advertisingmarketplace.posting;

import com.advertisingmarketplace.common.exception.ApiException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;

@UtilityClass
public class EmbeddedImages {

    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*]\\(\\s*([^)\\s]*)[^)]*\\)");
    private static final Pattern IMAGE_REF = Pattern.compile(
            "image:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    public static Set<UUID> referencedIn(String text) {
        Set<UUID> ids = new LinkedHashSet<>();
        Matcher matcher = MARKDOWN_IMAGE.matcher(text);
        while (matcher.find()) {
            String target = matcher.group(1);
            Matcher ref = IMAGE_REF.matcher(target);
            if (!ref.matches()) {
                throw new InvalidPostingContentException(
                        "Only uploaded images can be embedded, as ![caption](image:<id>); found: " + abbreviate(target));
            }
            ids.add(UUID.fromString(ref.group(1)));
        }
        return ids;
    }

    private static String abbreviate(String value) {
        return value.length() <= 80 ? value : value.substring(0, 77) + "...";
    }

    public static class InvalidPostingContentException extends ApiException {

        public InvalidPostingContentException(String message) {
            super(HttpStatus.BAD_REQUEST, message);
        }
    }
}
