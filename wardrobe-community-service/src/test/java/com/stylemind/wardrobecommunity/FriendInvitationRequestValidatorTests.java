package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.validation.friendship.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;

class FriendInvitationRequestValidatorTests {
    private final jakarta.validation.ValidatorFactory factory=Validation.buildDefaultValidatorFactory();
    private final FriendInvitationRequestValidator validator=new FriendInvitationRequestValidator(
            new ObjectMapper(),factory.getValidator(),new FriendshipRequestValidator());
    @AfterEach void close(){factory.close();}
    @Test void emailIsNormalizedBeforeValidation(){
        assertThat(validator.recipient("{\"recipient_email\":\" Friend+tag@Example.Test \"}")).isEqualTo("friend+tag@example.test");
    }
    @Test void duplicateFieldsUnknownFieldsTrailingJsonAndWrongTypesAreRejected(){
        for(String body:new String[]{"{}","null","[]","{\"recipient_email\":null}","{\"recipient_email\":123}",
            "{\"recipient_email\":\"x@example.test\",\"owner\":\"spoof\"}",
            "{\"recipient_email\":\"x@example.test\",\"recipient_email\":\"y@example.test\"}",
            "{\"recipient_email\":\"x@example.test\"} {}"})
            assertThatThrownBy(()->validator.recipient(body)).isInstanceOf(ApiOperationException.class);
    }
    @Test void blankMalformedAndTooLongEmailAreRejected(){
        for(String email:new String[]{""," ","invalid","a@","a\n@example.test","x".repeat(255)+"@example.test"})
            assertThatThrownBy(()->validator.recipient(new ObjectMapper().writeValueAsString(
                java.util.Map.of("recipient_email",email)))).isInstanceOf(ApiOperationException.class);
    }
    @Test void emptyActionsRequireExactlyAnEmptyObject(){
        validator.empty("{}");
        for(String body:new String[]{"","null","[]","{\"token\":\"unused\"}","{} {}"})
            assertThatThrownBy(()->validator.empty(body)).isInstanceOf(ApiOperationException.class);
    }
    @Test void linkRequiresCanonicalUuidAndOpaqueUnmodifiedCredential(){
        String id="00000000-0000-0000-0000-000000000001";String token="A".repeat(43);
        var value=validator.link("{\"friend_invitation_id\":\""+id+"\",\"token\":\""+token+"\"}");
        assertThat(value.friendInvitationId().toString()).isEqualTo(id);
        assertThat(value.token().equals(token)).isTrue();assertThat(value.toString().contains(token)).isFalse();
        for(String invalid:new String[]{"x","A".repeat(42),token+"=",token+" "})
            assertThatThrownBy(()->validator.link("{\"friend_invitation_id\":\""+id+"\",\"token\":\""+invalid+"\"}"))
                .isInstanceOf(ApiOperationException.class);
    }
}
