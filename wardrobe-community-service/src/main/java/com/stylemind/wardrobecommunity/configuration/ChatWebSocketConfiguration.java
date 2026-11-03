package com.stylemind.wardrobecommunity.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.websocket.ChatSocketInboundInterceptor;
import com.stylemind.wardrobecommunity.websocket.ChatSocketOutboundInterceptor;
import com.stylemind.wardrobecommunity.websocket.ChatStompErrorHandler;
import java.net.URI;
import java.util.Map;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class ChatWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final ChatSocketInboundInterceptor inbound;
    private final ChatSocketOutboundInterceptor outbound;
    private final ChatStompErrorHandler errors;
    private final String frontendOrigin;
    private final ObjectMapper mapper;

    public ChatWebSocketConfiguration(ChatSocketInboundInterceptor inbound, ChatSocketOutboundInterceptor outbound,
            ChatStompErrorHandler errors, ObjectMapper mapper,
            @Value("${stylemind.frontend.base-url:http://localhost:3000}") String frontendUrl) {
        this.inbound = inbound;
        this.outbound = outbound;
        this.errors = errors;
        this.mapper = mapper;
        URI uri = URI.create(frontendUrl);
        if (uri.getHost() == null || uri.getUserInfo() != null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("A trusted frontend origin is required for Chat WebSocket");
        }
        frontendOrigin = uri.getScheme() + "://" + uri.getRawAuthority();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(errors);
        // The ordered inbound decorator swallows interceptor failures, preventing STOMP ERROR.
        // Database locks and sequence numbers provide conversation ordering across all senders.
        registry.addEndpoint("/ws/chat").setAllowedOrigins(frontendOrigin).addInterceptors(new HandshakeInterceptor() {
            @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler handler, Map<String, Object> attributes) {
                return request.getURI().getRawQuery() == null;
            }
            @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    WebSocketHandler handler, Exception exception) { }
        });
    }

    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) { registration.interceptors(inbound); }
    @Override public boolean configureMessageConverters(List<MessageConverter> converters) {
        var json = new MappingJackson2MessageConverter();
        json.setObjectMapper(mapper);
        converters.add(json);
        return true;
    }
    @Override public void configureClientOutboundChannel(ChannelRegistration registration) { registration.interceptors(outbound); }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(65536).setSendBufferSizeLimit(524288).setSendTimeLimit(10000).setTimeToFirstMessage(15000);
    }
}

