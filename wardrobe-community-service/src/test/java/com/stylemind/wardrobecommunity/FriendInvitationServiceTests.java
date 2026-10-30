package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.stylemind.wardrobecommunity.client.identity.*;
import com.stylemind.wardrobecommunity.client.identity.payload.*;
import com.stylemind.wardrobecommunity.client.notification.FriendInvitationMailClient;
import com.stylemind.wardrobecommunity.dto.friendship.FriendInvitationActionResponse;
import com.stylemind.wardrobecommunity.errors.*;
import com.stylemind.wardrobecommunity.model.friendship.*;
import com.stylemind.wardrobecommunity.service.friendship.impl.FriendInvitationServiceImpl;
import com.stylemind.wardrobecommunity.service.friendship.internal.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;

class FriendInvitationServiceTests {
    final UUID actor=UUID.randomUUID(),invitation=UUID.randomUUID();
    final IdentityEmailVerificationClient identity=mock(IdentityEmailVerificationClient.class);
    final IdentityDirectoryClient directory=mock(IdentityDirectoryClient.class);
    final FriendInvitationPersistenceService persistence=mock(FriendInvitationPersistenceService.class);
    final FriendInvitationMailClient mail=mock(FriendInvitationMailClient.class);
    @BeforeEach void setup(){
        when(identity.lookup(actor)).thenReturn(Optional.of(new AccountEmailVerificationItem(actor,"a@example.test","ACTIVE",OffsetDateTime.now())));
        when(identity.lookupByEmail("b@example.test")).thenReturn(Optional.empty());
    }
    FriendInvitationServiceImpl service(String url){
        return new FriendInvitationServiceImpl(identity,directory,persistence,mail,new FriendInvitationLinks(url));
    }
    @Test void missingFrontendConfigurationFailsBeforeCreatingStateOrSendingMail(){
        assertThatThrownBy(()->service("").create(actor,"b@example.test")).isInstanceOf(ApiOperationException.class)
            .hasMessage("FRIEND_INVITATION_UNAVAILABLE");
        verifyNoInteractions(persistence,mail,directory);
    }
    @Test void directoryFailureOccursBeforeInvitationCommit(){
        when(directory.lookup(anyCollection())).thenThrow(new ApiOperationException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"IDENTITY_UNAVAILABLE","Unavailable"));
        assertThatThrownBy(()->service("https://frontend.example.test/friends").create(actor,"b@example.test"))
            .hasMessage("IDENTITY_UNAVAILABLE");
        verifyNoInteractions(persistence,mail);
    }
    @Test void returningExistingIncomingNeverSendsEmailOrAutomaticallyAccepts(){
        when(directory.lookup(anyCollection())).thenReturn(Map.of());
        var expected=new InvitationActionResult(new FriendInvitationActionResponse(invitation,"PENDING","INCOMING_EXISTS",null,OffsetDateTime.now().plusDays(7),0),200);
        when(persistence.prepareCreate(actor,"a@example.test","b@example.test",null))
            .thenReturn(new InvitationPreparation(expected,null,null,null));
        assertThat(service("https://frontend.example.test/friends").create(actor,"b@example.test")).isEqualTo(expected);
        verifyNoInteractions(mail);verify(persistence,never()).accept(any(),any(),any(),any());
    }
}
