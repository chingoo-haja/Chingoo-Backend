package com.ldsilver.chingoohaja.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;


@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofMillis(5000))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS)));

        // Exchange Strategies 설정 (메모리 제한)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(1024 * 1024);
                    // 개발 환경에서만 로깅 활성화
                    if (log.isDebugEnabled()) {
                        configurer.defaultCodecs().enableLoggingRequestDetails(true);
                    }
                })
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (log.isDebugEnabled()) {
                log.debug("Request: {} {}", clientRequest.method(), clientRequest.url());
                clientRequest.headers().forEach((name, values) ->
                        values.forEach(value -> {
                            if (name.equalsIgnoreCase("Authorization")) {
                                log.debug("{}: {}", name, maskToken(value));
                            } else {
                                log.debug("{}: {}", name, value);
                            }
                        })
                );
            }
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (log.isDebugEnabled()) {
                log.debug("Response: {}", clientResponse.statusCode());
            }
            return Mono.just(clientResponse);
        });
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        if (token.startsWith("Bearer ")) {
            String actualToken = token.substring(7);
            return "Bearer " +actualToken.substring(0, 4) + "***" + actualToken.substring(actualToken.length()-4);
        }
        return token.substring(0,4) + "***" + token.substring(token.length()-4);
    }
}
