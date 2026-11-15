package com.advertisingmarketplace.messaging.safety;

import com.advertisingmarketplace.messaging.dto.MessagePage;
import com.advertisingmarketplace.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    public record ReportRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record ResolveRequest(@NotNull ReportResolution resolution) {
    }

    @PostMapping("/api/v1/messages/{messageId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportService.ReportResponse report(@PathVariable UUID messageId, @Valid @RequestBody ReportRequest request,
            Authentication authentication) {
        return reportService.report(messageId, CurrentUser.id(authentication), request.reason());
    }

    @GetMapping("/api/v1/admin/reports")
    public PagedModel<ReportService.ReportResponse> list(@RequestParam(defaultValue = "OPEN") ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return new PagedModel<>(reportService.list(status, pageable));
    }

    @GetMapping("/api/v1/admin/reports/{id}")
    public ReportService.ReportResponse get(@PathVariable UUID id) {
        return reportService.get(id);
    }

    @PostMapping("/api/v1/admin/reports/{id}/resolve")
    public ReportService.ReportResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request,
            Authentication authentication) {
        return reportService.resolve(id, CurrentUser.id(authentication), request.resolution());
    }

    @GetMapping("/api/v1/admin/conversations/{conversationId}/messages")
    public MessagePage reportedConversation(@PathVariable UUID conversationId,
            @RequestParam(required = false) UUID before, @RequestParam(defaultValue = "50") int limit) {
        return reportService.reportedConversationMessages(conversationId, before, limit);
    }
}
