package com.advertisingmarketplace.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class ManagementPortRequestMatcher implements RequestMatcher, ApplicationListener<WebServerInitializedEvent> {

    static final String MANAGEMENT_NAMESPACE = "management";

    private final AtomicInteger managementPort = new AtomicInteger(-1);

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (MANAGEMENT_NAMESPACE.equals(event.getApplicationContext().getServerNamespace())) {
            managementPort.set(event.getWebServer().getPort());
        }
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        int port = managementPort.get();
        return port > 0 && request.getLocalPort() == port;
    }
}
