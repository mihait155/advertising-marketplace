package com.advertisingmarketplace.messaging.safety;

import com.advertisingmarketplace.common.exception.ApiException;
import com.advertisingmarketplace.common.exception.NotFoundException;
import com.advertisingmarketplace.messaging.Message;
import com.advertisingmarketplace.messaging.MessageRepository;
import com.advertisingmarketplace.messaging.MessageType;
import com.advertisingmarketplace.messaging.MessagingExceptions.ConversationNotFoundException;
import com.advertisingmarketplace.messaging.MessagingExceptions.MessageNotFoundException;
import com.advertisingmarketplace.messaging.MessagingService;
import com.advertisingmarketplace.messaging.dto.MessagePage;
import com.advertisingmarketplace.messaging.dto.MessageResponse;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final MessageReportRepository reportRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessagingService messagingService;
    private final Clock clock;

    @Transactional
    public ReportResponse report(UUID messageId, UUID reporterId, String reason) {
        Message message = messageRepository.findDetailedById(messageId)
                .filter(m -> m.getConversation().isParticipant(reporterId))
                .orElseThrow(() -> new MessageNotFoundException(messageId));
        if (message.getType() != MessageType.USER || message.isSentBy(reporterId)) {
            throw new CannotReportException();
        }
        if (reportRepository.existsByMessageIdAndReporterId(messageId, reporterId)) {
            throw new AlreadyReportedException();
        }
        MessageReport report = reportRepository.save(new MessageReport(message,
                userRepository.getReferenceById(reporterId), reason.strip(), now()));
        return toResponse(report);
    }

    public Page<ReportResponse> list(ReportStatus status, Pageable pageable) {
        return reportRepository.findAllByStatus(status, pageable).map(this::toResponse);
    }

    public ReportResponse get(UUID reportId) {
        return toResponse(findReport(reportId));
    }

    @Transactional
    public ReportResponse resolve(UUID reportId, UUID adminId, ReportResolution resolution) {
        MessageReport report = findReport(reportId);
        if (report.getStatus() == ReportStatus.RESOLVED) {
            throw new ReportAlreadyResolvedException();
        }
        var admin = userRepository.getReferenceById(adminId);
        if (resolution == ReportResolution.MESSAGE_DELETED) {
            messagingService.deleteAsModerator(report.getMessage().getId(), adminId);
            reportRepository.findAllByMessageIdAndStatus(report.getMessage().getId(), ReportStatus.OPEN)
                    .forEach(other -> other.resolve(ReportResolution.MESSAGE_DELETED, admin, now()));
        }
        report.resolve(resolution, admin, now());
        return toResponse(report);
    }

    public MessagePage reportedConversationMessages(UUID conversationId, UUID before, int limit) {
        if (!reportRepository.existsByMessageConversationId(conversationId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        return messagingService.page(conversationId, before, limit);
    }

    private MessageReport findReport(UUID reportId) {
        return reportRepository.findDetailedById(reportId).orElseThrow(() -> new ReportNotFoundException(reportId));
    }

    private ReportResponse toResponse(MessageReport report) {
        MessageResponse message = messagingService.loadMessage(report.getMessage().getId());
        return new ReportResponse(report.getId(), report.getMessage().getConversation().getId(), message,
                UserSummary.from(report.getReporter()), report.getReason(), report.getStatus(), report.getResolution(),
                report.getResolvedBy() == null ? null : UserSummary.from(report.getResolvedBy()),
                report.getCreatedAt(), report.getResolvedAt());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public record ReportResponse(UUID id, UUID conversationId, MessageResponse message, UserSummary reporter,
            String reason, ReportStatus status, ReportResolution resolution, UserSummary resolvedBy, Instant createdAt,
            Instant resolvedAt) {
    }

    public static class CannotReportException extends ApiException {

        public CannotReportException() {
            super(HttpStatus.BAD_REQUEST, "You can only report messages other users sent you");
        }
    }

    public static class AlreadyReportedException extends ApiException {

        public AlreadyReportedException() {
            super(HttpStatus.CONFLICT, "You already reported this message");
        }
    }

    public static class ReportAlreadyResolvedException extends ApiException {

        public ReportAlreadyResolvedException() {
            super(HttpStatus.CONFLICT, "This report was already resolved");
        }
    }

    public static class ReportNotFoundException extends NotFoundException {

        public ReportNotFoundException(UUID id) {
            super("Report with id " + id + " was not found");
        }
    }
}
