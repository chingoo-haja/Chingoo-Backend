package com.ldsilver.chingoohaja.infrastructure.oauth;

import com.ldsilver.chingoohaja.common.exception.CustomException;
import com.ldsilver.chingoohaja.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OAuthClientFactory {

    private final Map<String, OAuthClient> clientMap;

    public OAuthClientFactory(List<OAuthClient> clients) {
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(
                        OAuthClient::getProviderName,
                        Function.identity()
                ));
    }

    public OAuthClient getClient(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED);
        }

        String normalizedProvider = provider.toLowerCase().trim();
        OAuthClient client = clientMap.get(normalizedProvider);

        if (client == null) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_NOT_SUPPORTED, provider);
        }
        return client;
    }

    public List<String> getSupportedProviders() {
        return List.copyOf(clientMap.keySet());
    }

    public boolean isProviderSupported(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            return false;
        }
        return clientMap.containsKey(provider.toLowerCase().trim());
    }
}
