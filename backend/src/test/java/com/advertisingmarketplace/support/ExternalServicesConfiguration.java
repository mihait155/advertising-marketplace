package com.advertisingmarketplace.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@TestConfiguration(proxyBeanMethods = false)
public class ExternalServicesConfiguration {

    public static final String BUCKET = "test-images";

    @Bean
    GenericContainer<?> s3MockContainer() {
        GenericContainer<?> container = new GenericContainer<>("adobe/s3mock:4.7.0")
                .withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));
        container.start();
        return container;
    }

    @Bean
    GenericContainer<?> clamAvContainer() {
        GenericContainer<?> container = new GenericContainer<>("clamav/clamav:1.4")
                .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
                .withExposedPorts(3310)
                .waitingFor(Wait.forLogMessage(".*socket found, clamd started.*", 1)
                        .withStartupTimeout(java.time.Duration.ofMinutes(3)));
        container.start();
        return container;
    }

    @Bean
    GenericContainer<?> mailpitContainer() {
        GenericContainer<?> container = new GenericContainer<>("axllent/mailpit:v1.27")
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025).forStatusCode(200));
        container.start();
        return container;
    }

    @Bean
    Mailpit mailpit(GenericContainer<?> mailpitContainer) {
        return new Mailpit("http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025));
    }

    @Bean(destroyMethod = "close")
    FakeOAuthServer fakeOAuthServer() throws java.io.IOException {
        return new FakeOAuthServer();
    }

    @Bean(destroyMethod = "close")
    FakeEcbServer fakeEcbServer() throws java.io.IOException {
        return new FakeEcbServer();
    }

    @Bean
    DynamicPropertyRegistrar externalServiceProperties(GenericContainer<?> s3MockContainer,
            GenericContainer<?> clamAvContainer, FakeEcbServer fakeEcbServer, GenericContainer<?> mailpitContainer,
            FakeOAuthServer fakeOAuthServer) {
        return registry -> {
            for (String provider : new String[] {"twitch", "youtube"}) {
                String base = fakeOAuthServer.baseUrl() + "/" + provider;
                registry.add("app.social." + provider + ".client-id", () -> FakeOAuthServer.CLIENT_ID);
                registry.add("app.social." + provider + ".client-secret", () -> FakeOAuthServer.CLIENT_SECRET);
                registry.add("app.social." + provider + ".token-url", () -> base + "/token");
                registry.add("app.social." + provider + ".api-url",
                        () -> base + (provider.equals("twitch") ? "/helix" : "/v3"));
            }
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(1025));
            registry.add("app.exchange-rates.ecb-url", fakeEcbServer::url);
            registry.add("app.exchange-rates.refresh-on-startup", () -> "false");
            registry.add("app.storage.s3.endpoint",
                    () -> "http://" + s3MockContainer.getHost() + ":" + s3MockContainer.getMappedPort(9090));
            registry.add("app.storage.s3.bucket", () -> BUCKET);
            registry.add("app.storage.s3.path-style-access", () -> "true");
            registry.add("app.storage.s3.create-bucket", () -> "true");
            registry.add("app.storage.s3.access-key", () -> "test");
            registry.add("app.storage.s3.secret-key", () -> "test");
            registry.add("app.clamav.host", clamAvContainer::getHost);
            registry.add("app.clamav.port", () -> clamAvContainer.getMappedPort(3310));
        };
    }
}
