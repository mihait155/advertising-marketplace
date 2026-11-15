package com.advertisingmarketplace.messaging.safety;

import com.advertisingmarketplace.messaging.Message;
import com.advertisingmarketplace.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReport {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false, updatable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private User reporter;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution", length = 20)
    private ReportResolution resolution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MessageReport(Message message, User reporter, String reason, Instant now) {
        this.id = UUID.randomUUID();
        this.message = message;
        this.reporter = reporter;
        this.reason = reason;
        this.status = ReportStatus.OPEN;
        this.createdAt = now;
    }

    public void resolve(ReportResolution resolution, User admin, Instant now) {
        this.status = ReportStatus.RESOLVED;
        this.resolution = resolution;
        this.resolvedBy = admin;
        this.resolvedAt = now;
    }
}
