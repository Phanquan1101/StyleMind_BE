package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.stylemind.wardrobecommunity.dto.chat.ChatMessageResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.model.chat.*;
import com.stylemind.wardrobecommunity.service.chat.impl.ChatImageMessageServiceImpl;
import com.stylemind.wardrobecommunity.service.chat.internal.ChatMessagePersistenceService;
import com.stylemind.wardrobecommunity.storage.chat.*;
import com.stylemind.wardrobecommunity.validation.chat.ChatImageRequestValidator;
import com.stylemind.wardrobecommunity.websocket.ChatRealtimePublisher;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

class ChatImageMessageServiceTests {
    final UUID actor=UUID.randomUUID(),chat=UUID.randomUUID(),key=UUID.randomUUID(),attachment=UUID.randomUUID();
    final ChatMessagePersistenceService persistence=mock(ChatMessagePersistenceService.class);
    final ChatImageStorage storage=mock(ChatImageStorage.class);
    final ChatRealtimePublisher realtime=mock(ChatRealtimePublisher.class);
    final ChatImageMessageServiceImpl service=new ChatImageMessageServiceImpl(persistence,storage,realtime);
    ChatImage image(int marker){byte[] bytes={(byte)marker};return new ChatImage(bytes,"image/png",ChatImageRequestValidator.sha256(bytes));}
    ChatImageCommand command(){return new ChatImageCommand(key,null,List.of(image(1),image(2)));}
    ChatMessageResponse saved(){return new ChatMessageResponse(UUID.randomUUID(),chat,actor,key,1,null,OffsetDateTime.now(),List.of());}
    @Test void retryReturnsOriginalWithoutCallingCloudOrBroadcastingAgain(){
        var old=saved();
        // Commands contain byte arrays, so match the very same immutable command instance.
        var command=command();when(persistence.imageReplay(actor,chat,command)).thenReturn(Optional.of(old));
        assertThat(service.send(actor,chat,command).message()).isSameAs(old);
        verifyNoInteractions(storage,realtime);
    }
    @Test void newlyCommittedMessagePublishesOnlyAfterWriterReturns(){
        var command=command();var response=saved();
        when(persistence.imageReplay(actor,chat,command)).thenReturn(Optional.empty());
        when(persistence.sendImages(eq(actor),eq(chat),eq(command),anyList())).thenReturn(new ChatMessageSendResult(response,true));
        assertThat(service.send(actor,chat,command).created()).isTrue();
        var order=inOrder(persistence,storage,realtime);
        order.verify(persistence).imageReplay(actor,chat,command);order.verify(storage).requireAvailable();
        order.verify(storage,times(2)).put(anyString(),any());order.verify(persistence).sendImages(eq(actor),eq(chat),eq(command),anyList());
        order.verify(realtime).publish(response);verify(storage,never()).delete(anyString());
    }
    @Test void partialPutFailureCompensatesOnlyOwnAttemptAndNeverCallsWriter(){
        var command=command();when(persistence.imageReplay(actor,chat,command)).thenReturn(Optional.empty());
        doNothing().doThrow(unavailable()).when(storage).put(anyString(),any());
        assertThatThrownBy(()->service.send(actor,chat,command)).isInstanceOf(ApiOperationException.class);
        verify(storage,times(2)).delete(startsWith("chat/v1/"));
        verify(persistence,never()).sendImages(any(),any(),any(),anyList());verifyNoInteractions(realtime);
    }
    @Test void unfriendWinningAfterUploadCausesDefinitiveRollbackAndCompensation(){
        var command=command();when(persistence.imageReplay(actor,chat,command)).thenReturn(Optional.empty());
        when(persistence.sendImages(any(),any(),any(),anyList())).thenThrow(new ApiOperationException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Resource was not found"));
        assertThatThrownBy(()->service.send(actor,chat,command)).isInstanceOf(ApiOperationException.class);
        verify(storage,times(2)).delete(anyString());verifyNoInteractions(realtime);
    }
    @Test void unknownCommitOutcomeNeverDeletesPotentiallyCommittedImages(){
        var command=command();when(persistence.imageReplay(actor,chat,command)).thenReturn(Optional.empty());
        when(persistence.sendImages(any(),any(),any(),anyList())).thenThrow(new DataAccessResourceFailureException("simulated commit transport loss"));
        assertThatThrownBy(()->service.send(actor,chat,command)).isInstanceOf(DataAccessResourceFailureException.class);
        verify(storage,never()).delete(anyString());verifyNoInteractions(realtime);
    }
    @Test void concurrentIdenticalLoserReturnsWinnerAndCleansItsSeparateAttempt(){
        var command=command();var old=saved();when(persistence.imageReplay(actor,chat,command)).thenReturn(Optional.empty());
        when(persistence.sendImages(any(),any(),any(),anyList())).thenReturn(new ChatMessageSendResult(old,false));
        assertThat(service.send(actor,chat,command).message()).isSameAs(old);
        verify(storage,times(2)).delete(startsWith("chat/v1/"));verifyNoInteractions(realtime);
    }
    @Test void cleanupHasBoundedAttemptsAndDoesNotHideTheOriginalBusinessRejection(){
        var command=command();when(persistence.imageReplay(actor,chat,command)).thenReturn(Optional.empty());
        var rejection=new ApiOperationException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","Different payload");
        when(persistence.sendImages(any(),any(),any(),anyList())).thenThrow(rejection);
        doThrow(unavailable()).when(storage).delete(anyString());
        assertThatThrownBy(()->service.send(actor,chat,command)).isSameAs(rejection);
        verify(storage,times(6)).delete(anyString());
    }
    @Test void imageReadRechecksCurrentPermissionAfterStorageAndRejectsRevokedAccess(){
        var image=image(1);String object=ChatImageKeys.create(UUID.randomUUID(),1,image);
        when(persistence.attachment(actor,chat,attachment)).thenReturn(new ChatStoredImage(object,"image/png",1))
                .thenThrow(new ApiOperationException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Resource was not found"));
        when(storage.get(object)).thenReturn(new ChatAttachmentContent(image.bytes(),"image/png"));
        assertThatThrownBy(()->service.read(actor,chat,attachment)).isInstanceOf(ApiOperationException.class);
        verify(persistence,times(2)).attachment(actor,chat,attachment);
    }
    @Test void returnedImageBytesMustMatchStoredSizeMimeAndApplicationDigest(){
        var image=image(1);String object=ChatImageKeys.create(UUID.randomUUID(),1,image);
        when(persistence.attachment(actor,chat,attachment)).thenReturn(new ChatStoredImage(object,"image/png",1));
        when(storage.get(object)).thenReturn(new ChatAttachmentContent(new byte[]{9},"image/png"));
        assertThatThrownBy(()->service.read(actor,chat,attachment)).isInstanceOfSatisfying(ApiOperationException.class,e->assertThat(e.code()).isEqualTo("CHAT_IMAGE_STORAGE_UNAVAILABLE"));
        when(storage.get(object)).thenReturn(new ChatAttachmentContent(image.bytes(),"image/png"));
        assertThat(service.read(actor,chat,attachment).bytes()).isEqualTo(image.bytes());
    }
    ApiOperationException unavailable(){return new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE,"CHAT_IMAGE_STORAGE_UNAVAILABLE","Unavailable");}
}
