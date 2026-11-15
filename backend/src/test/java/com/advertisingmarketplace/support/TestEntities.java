package com.advertisingmarketplace.support;

import com.advertisingmarketplace.image.Image;
import com.advertisingmarketplace.offer.Offer;
import com.advertisingmarketplace.posting.Posting;
import com.advertisingmarketplace.posting.PostingCategory;
import com.advertisingmarketplace.posting.PostingStatus;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserType;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TestEntities {

    private TestEntities() {
    }

    public static User user(UserType type) {
        User user = new User("User " + type, type.name().toLowerCase() + "@example.com", null, type, "{bcrypt}x");
        return withId(user, UUID.randomUUID());
    }

    public static Posting posting(User creator, PostingCategory category, String text) {
        return posting(creator, category, PostingStatus.OPEN, text);
    }

    public static Posting posting(User creator, PostingCategory category, PostingStatus status, String text) {
        return withId(new Posting(creator, category, status, "Title", text, null, null, null), UUID.randomUUID());
    }

    public static Offer offer(Posting posting, User offerer) {
        return withId(new Offer(posting, offerer, "Offer text", new BigDecimal("100.00"), new BigDecimal("200.00"), "EUR"),
                UUID.randomUUID());
    }

    public static Image image(User owner) {
        Image image = new Image(owner, "images/" + UUID.randomUUID() + ".png", "image/png", 10, "ab",
                Instant.parse("2026-01-01T10:00:00Z"));
        return withId(image, UUID.randomUUID());
    }

    public static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T withField(T entity, String name, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(entity, value);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T withId(T entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
