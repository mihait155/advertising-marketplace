package com.advertisingmarketplace.messaging.notification;

import com.advertisingmarketplace.messaging.Conversation;
import com.advertisingmarketplace.messaging.ConversationRepository;
import com.advertisingmarketplace.messaging.Message;
import com.advertisingmarketplace.messaging.MessageRepository;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class MessageNotificationJob {

    private static final int PREVIEW_LENGTH = 200;
    private static final int MAX_PREVIEW_MESSAGES = 5;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final NotificationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public MessageNotificationJob(ConversationRepository conversationRepository, MessageRepository messageRepository,
            ObjectProvider<JavaMailSender> mailSender, NotificationProperties properties,
            TransactionTemplate transactionTemplate, Clock clock) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.mailSender = mailSender;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notifications.email.cron:30 * * * * *}")
    public int run() {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!properties.enabled() || sender == null) {
            return 0;
        }
        Integer sent = transactionTemplate.execute(status -> {
            Instant threshold = clock.instant().minus(properties.delay());
            int count = 0;
            count += notify(conversationRepository.lockCreatorsToNotify(threshold, properties.batchSize()),
                    Conversation::getCreator, sender);
            count += notify(conversationRepository.lockCounterpartsToNotify(threshold, properties.batchSize()),
                    Conversation::getCounterpart, sender);
            return count;
        });
        return sent == null ? 0 : sent;
    }

    private int notify(List<UUID> conversationIds, Function<Conversation, User> recipientOf, JavaMailSender sender) {
        int sent = 0;
        for (UUID id : conversationIds) {
            Conversation conversation = conversationRepository.findDetailedById(id).orElse(null);
            if (conversation == null || recipientOf.apply(conversation) == null) {
                continue;
            }
            User recipient = recipientOf.apply(conversation);
            Instant after = latest(conversation.lastReadAt(recipient.getId()),
                    conversation.notifiedUpTo(recipient.getId()));
            List<Message> unread = messageRepository.findUnreadFor(id, recipient.getId(), after);
            if (unread.isEmpty()) {
                continue;
            }
            Instant upTo = unread.getLast().getCreatedAt();
            if (recipient.isEmailMessageNotifications()) {
                try {
                    sender.send(email(conversation, recipient, unread));
                    sent++;
                } catch (MailException e) {
                    log.warn("Could not send message notification for conversation {}: {}", id, e.getMessage());
                    continue;
                }
            }
            conversation.markNotified(recipient.getId(), upTo);
        }
        return sent;
    }

    private SimpleMailMessage email(Conversation conversation, User recipient, List<Message> unread) {
        String other = UserSummary.fromNullable(conversation.otherParticipant(recipient.getId()).orElse(null))
                .fullName();
        String title = conversation.getPosting().getTitle();
        String previews = unread.stream().skip(Math.max(0, unread.size() - MAX_PREVIEW_MESSAGES))
                .map(this::preview)
                .collect(Collectors.joining("\n"));
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(recipient.getEmail());
        mail.setSubject(unread.size() == 1
                ? "New message from " + other + " about \"" + title + "\""
                : unread.size() + " new messages from " + other + " about \"" + title + "\"");
        mail.setText("""
                Hi %s,

                You have %d unread message%s about "%s":

                %s

                Reply: %s/conversations/%s

                You can turn these emails off in your notification settings.
                """.formatted(recipient.getFullName(), unread.size(), unread.size() == 1 ? "" : "s", title, previews,
                properties.frontendUrl(), conversation.getId()));
        return mail;
    }

    private String preview(Message message) {
        String name = UserSummary.fromNullable(message.getSender()).fullName();
        String text = message.getText() == null ? "(image)" : message.getText();
        if (text.length() > PREVIEW_LENGTH) {
            text = text.substring(0, PREVIEW_LENGTH - 3) + "...";
        }
        return "- " + name + ": " + text;
    }

    private static Instant latest(Instant a, Instant b) {
        if (a == null) {
            return b == null ? Instant.EPOCH : b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }
}
