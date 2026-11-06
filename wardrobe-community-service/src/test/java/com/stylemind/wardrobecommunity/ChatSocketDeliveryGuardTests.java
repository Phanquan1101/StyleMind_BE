package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.websocket.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.support.MessageBuilder;

class ChatSocketDeliveryGuardTests {
    static final UUID ACTOR=new UUID(0,1),CHAT=new UUID(0,2);
    static final Instant NOW=Instant.parse("2026-10-27T12:00:00Z");

    @Test void droppedDeliveryAdvancesOrderedQueueSoRefriendCanReceiveFutureMessages() {
        var sessions=new ChatSocketSessions(Clock.fixed(NOW,ZoneOffset.UTC));
        var guard=new ChatSocketOutboundInterceptor(sessions,mock(ChatSocketAccess.class));
        var next=mock(Runnable.class);
        var h=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        h.setSessionId("unauthenticated");
        h.setHeader("simpNextMessageTask",next);
        var queued=MessageBuilder.createMessage(new byte[0],h.getMessageHeaders());
        assertThat(guard.beforeHandle(queued,null,null)).isNull();
        verify(next,times(1)).run();
    }

    @Test void rejectedConnectDoesNotAttachCredentialsOrPayloadToFrameworkException() {
        var decoder=mock(org.springframework.security.oauth2.jwt.JwtDecoder.class);
        var converter=new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        var sessions=new ChatSocketSessions(Clock.fixed(NOW,ZoneOffset.UTC));
        var access=mock(ChatSocketAccess.class);
        var inbound=new ChatSocketInboundInterceptor(decoder,converter,sessions,access);
        when(decoder.decode("private-test-credential")).thenThrow(new org.springframework.security.oauth2.jwt.JwtException("Invalid JWT"));
        var headers=org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
        headers.setSessionId("socket");
        headers.setNativeHeader("Authorization","Bearer private-test-credential");
        headers.setLeaveMutable(true);
        var frame=MessageBuilder.createMessage("private-text-marker".getBytes(java.nio.charset.StandardCharsets.UTF_8),headers.getMessageHeaders());
        var error=org.junit.jupiter.api.Assertions.assertThrows(MessageDeliveryException.class,()->inbound.preSend(frame,null));
        assertThat(error.getFailedMessage()).isNull();
        assertThat(error.getMessage()).doesNotContain("private-test-credential","private-text-marker","Authorization");
        assertThat(headers.getNativeHeader("Authorization")).isNull();
    }

    @Test void queuedDeliveryChecksRevokedFriendshipAtHandlerTimeRatherThanTrustingPastAuthorization() {
        var sessions=new ChatSocketSessions(Clock.fixed(NOW,ZoneOffset.UTC));
        sessions.register("socket",ACTOR,NOW.plusSeconds(60));
        var access=mock(ChatSocketAccess.class);
        var guard=new ChatSocketOutboundInterceptor(sessions,access);
        Message<byte[]> queued=message("socket",CHAT.toString(),"/topic/chats/"+CHAT);
        assertThat(guard.beforeHandle(queued,null,null)).isSameAs(queued);
        doThrow(new ApiOperationException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Resource was not found"))
                .when(access).require(ACTOR,CHAT);
        assertThat(guard.beforeHandle(queued,null,null)).isNull();
        verify(access,times(2)).require(ACTOR,CHAT);
    }

    @Test void tokenExpiresExactlyAtDeadlineAndUnknownSocketCannotReceiveQueuedData() {
        Clock clock=mock(Clock.class);when(clock.instant()).thenReturn(NOW);
        var sessions=new ChatSocketSessions(clock);sessions.register("socket",ACTOR,NOW.plusSeconds(1));
        var access=mock(ChatSocketAccess.class);var guard=new ChatSocketOutboundInterceptor(sessions,access);
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        assertThat(guard.beforeHandle(message("socket",CHAT.toString(),"/topic/chats/"+CHAT),null,null)).isNull();
        assertThat(guard.beforeHandle(message("unknown",CHAT.toString(),"/topic/chats/"+CHAT),null,null)).isNull();
        verifyNoInteractions(access);
    }

    @Test void unexpectedDestinationsAndDatabaseFailureFailClosedWithoutPayloadReflection() {
        var sessions=new ChatSocketSessions(Clock.fixed(NOW,ZoneOffset.UTC));sessions.register("socket",ACTOR,NOW.plusSeconds(60));
        var access=mock(ChatSocketAccess.class);var guard=new ChatSocketOutboundInterceptor(sessions,access);
        assertThat(guard.beforeHandle(message("socket",null,"/queue/someone-else"),null,null)).isNull();
        doThrow(new IllegalStateException("simulated DB outage")).when(access).require(ACTOR,CHAT);
        assertThat(guard.beforeHandle(message("socket",CHAT.toString(),"/topic/chats/"+CHAT),null,null)).isNull();
    }

    @Test void sanitizedProtocolErrorCanReachConnectionRejectedBeforeAuthentication() {
        var sessions=new ChatSocketSessions(Clock.fixed(NOW,ZoneOffset.UTC));
        var access=mock(ChatSocketAccess.class);var guard=new ChatSocketOutboundInterceptor(sessions,access);
        var error=new ChatStompErrorHandler().handleClientMessageProcessingError(null,
                new ApiOperationException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","do not reflect this private marker"));
        assertThat(guard.beforeHandle(error,null,null)).isSameAs(error);
        assertThat(new String(error.getPayload(),java.nio.charset.StandardCharsets.UTF_8))
                .contains("UNAUTHENTICATED").doesNotContain("private marker");
        verifyNoInteractions(access);
    }

    Message<byte[]> message(String session,String chat,String destination) {
        var h=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);h.setSessionId(session);h.setDestination(destination);
        if(chat!=null)h.setHeader(ChatSocketAccess.CHAT_HEADER,chat);
        return MessageBuilder.createMessage(new byte[0],h.getMessageHeaders());
    }
}

