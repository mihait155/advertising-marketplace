package com.advertisingmarketplace.messaging.safety;

import com.advertisingmarketplace.common.exception.ApiException;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.dto.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

    private final UserBlockRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    public boolean isBlockedEitherWay(UUID a, UUID b) {
        return a != null && b != null && repository.existsBetween(a, b);
    }

    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new CannotBlockSelfException();
        }
        if (!userRepository.existsById(blockedId)) {
            throw new UserNotFoundException(blockedId);
        }
        UserBlockId id = new UserBlockId(blockerId, blockedId);
        if (!repository.existsById(id)) {
            repository.save(new UserBlock(id, clock.instant()));
        }
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        repository.deleteById(new UserBlockId(blockerId, blockedId));
    }

    public List<BlockedUserResponse> listBlocked(UUID blockerId) {
        List<UserBlock> blocks = repository.findAllByIdBlockerIdOrderByCreatedAtDesc(blockerId);
        var users = userRepository.findAllById(blocks.stream().map(b -> b.getId().blockedId()).toList()).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u));
        return blocks.stream()
                .map(b -> new BlockedUserResponse(UserSummary.from(users.get(b.getId().blockedId())), b.getCreatedAt()))
                .toList();
    }

    public record BlockedUserResponse(UserSummary user, Instant blockedAt) {
    }

    public static class CannotBlockSelfException extends ApiException {

        public CannotBlockSelfException() {
            super(HttpStatus.BAD_REQUEST, "You can't block yourself");
        }
    }
}
