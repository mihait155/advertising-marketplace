package com.advertisingmarketplace.social;

import com.advertisingmarketplace.common.observability.MarketplaceMetrics;
import com.advertisingmarketplace.social.SocialExceptions.ChannelAlreadyLinkedException;
import com.advertisingmarketplace.social.SocialExceptions.InvalidGrantException;
import com.advertisingmarketplace.social.SocialExceptions.MarketersOnlyException;
import com.advertisingmarketplace.social.SocialExceptions.ProviderNotAvailableException;
import com.advertisingmarketplace.social.SocialExceptions.SocialAccountNotFoundException;
import com.advertisingmarketplace.social.dto.SocialAccountResponse;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserNotFoundException;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserType;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class SocialAccountService {

    private static final int SYNC_BATCH = 200;

    private final Map<SocialProvider, SocialPlatformClient> clients;
    private final SocialAccountRepository repository;
    private final UserRepository userRepository;
    private final OAuthStateService stateService;
    private final TokenCipher cipher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final MarketplaceMetrics metrics;

    public SocialAccountService(List<SocialPlatformClient> clients, SocialAccountRepository repository,
            UserRepository userRepository, OAuthStateService stateService, TokenCipher cipher,
            TransactionTemplate transactionTemplate, Clock clock, MarketplaceMetrics metrics) {
        this.clients = clients.stream().collect(Collectors.toMap(SocialPlatformClient::provider, Function.identity()));
        this.repository = repository;
        this.userRepository = userRepository;
        this.stateService = stateService;
        this.cipher = cipher;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.metrics = metrics;
    }

    public record ProviderInfo(SocialProvider provider, String name, boolean available) {
    }

    public List<ProviderInfo> providers() {
        return List.of(SocialProvider.values()).stream()
                .map(p -> new ProviderInfo(p, p.displayName(), clients.containsKey(p) && clients.get(p).isConfigured()))
                .toList();
    }

    public URI authorizationUrl(UUID userId, SocialProvider provider) {
        requireMarketer(userId);
        return client(provider).authorizationUrl(stateService.issue(userId, provider));
    }

    @Transactional
    public SocialAccountResponse connect(UUID userId, String code, String state) {
        User user = requireMarketer(userId);
        SocialProvider provider = stateService.verify(state, userId);
        SocialPlatformClient client = client(provider);
        OAuthTokens tokens = client.exchangeCode(code);
        ChannelSnapshot snapshot = client.fetchChannel(tokens.accessToken());
        repository.findByProviderAndExternalId(provider, snapshot.externalId())
                .filter(existing -> !existing.getUser().getId().equals(userId))
                .ifPresent(existing -> {
                    throw new ChannelAlreadyLinkedException(provider);
                });
        String encrypted = tokens.refreshToken() == null ? null : cipher.encrypt(tokens.refreshToken());
        Instant now = now();
        SocialAccount account = repository.findByUserIdAndProvider(userId, provider)
                .map(existing -> {
                    existing.update(snapshot, encrypted, now);
                    existing.reconnect(now);
                    return existing;
                })
                .orElseGet(() -> repository.save(new SocialAccount(user, provider, snapshot, encrypted, now)));
        return SocialAccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<SocialAccountResponse> accountsOf(UUID userId) {
        return repository.findAllByUserIdOrderByFollowersDesc(userId).stream().map(SocialAccountResponse::from).toList();
    }

    @Transactional
    public void disconnect(UUID userId, UUID accountId) {
        SocialAccount account = repository.findById(accountId)
                .filter(a -> a.getUser().getId().equals(userId))
                .orElseThrow(() -> new SocialAccountNotFoundException(accountId));
        repository.delete(account);
    }

    @Scheduled(cron = "${app.social.sync-cron:0 40 4 * * *}")
    public int syncAll() {
        int synced = 0;
        for (UUID id : repository.findSyncable(Limit.of(SYNC_BATCH))) {
            Boolean ok = transactionTemplate.execute(status -> repository.findById(id).map(this::sync).orElse(false));
            if (Boolean.TRUE.equals(ok)) {
                synced++;
            }
        }
        if (synced > 0) {
            log.info("Synced audience stats for {} connected accounts", synced);
        }
        return synced;
    }

    private boolean sync(SocialAccount account) {
        SocialPlatformClient client = clients.get(account.getProvider());
        if (client == null || !client.isConfigured()) {
            return false;
        }
        try {
            OAuthTokens tokens = client.refresh(cipher.decrypt(account.getEncryptedRefreshToken()));
            ChannelSnapshot snapshot = client.fetchChannel(tokens.accessToken());
            account.update(snapshot, tokens.refreshToken() == null ? null : cipher.encrypt(tokens.refreshToken()),
                    now());
            metrics.socialSync(account.getProvider().name().toLowerCase(), "success");
            return true;
        } catch (InvalidGrantException | IllegalArgumentException e) {
            account.markNeedsReconnect();
            metrics.socialSync(account.getProvider().name().toLowerCase(), "needs_reconnect");
            log.info("Connected account {} needs to be reconnected", account.getId());
            return false;
        } catch (RuntimeException e) {
            log.warn("Could not sync connected account {}: {}", account.getId(), e.getMessage());
            metrics.socialSync(account.getProvider().name().toLowerCase(), "failure");
            return false;
        }
    }

    private SocialPlatformClient client(SocialProvider provider) {
        SocialPlatformClient client = clients.get(provider);
        if (client == null || !client.isConfigured()) {
            throw new ProviderNotAvailableException(provider);
        }
        return client;
    }

    private User requireMarketer(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getUserType() != UserType.MARKETER) {
            throw new MarketersOnlyException();
        }
        return user;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
