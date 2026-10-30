package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.validation.chat.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;

class ChatImageRequestValidatorTests {
    final ObjectMapper mapper=new ObjectMapper();
    final ChatSessionRequestValidator ids=new ChatSessionRequestValidator(mapper);
    final ChatImageRequestValidator validator=new ChatImageRequestValidator(mapper,ids,new ChatMessageRequestValidator(mapper,ids));
    @Test void jpegPngAndWebpAreDecodedFromBytesAndRetainTheirOriginalContent() throws Exception {
        for(String format:new String[]{"png","jpg"}){
            byte[] bytes=picture(format);
            var image=validator.image(new MockMultipartFile("images","ignored-name",null,bytes));
            assertThat(image.bytes()).isEqualTo(bytes);
            assertThat(image.contentType()).isEqualTo(format.equals("jpg")?"image/jpeg":"image/png");
        }
        byte[] webp=Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");
        assertThat(validator.image(new MockMultipartFile("images","photo.webp","image/webp",webp)).contentType()).isEqualTo("image/webp");
    }
    @Test void emptyTooLargeAndSpoofedImagesAreRejected() throws Exception {
        assertInvalid(()->validator.image(new MockMultipartFile("images",new byte[0])));
        assertInvalid(()->validator.image(new MockMultipartFile("images",new byte[ChatImageRequestValidator.MAX_BYTES+1])));
        assertInvalid(()->validator.image(new MockMultipartFile("images","x.png","image/png","not an image".getBytes(StandardCharsets.UTF_8))));
        assertInvalid(()->validator.image(new MockMultipartFile("images","x.jpg","image/jpeg",picture("png"))));
        assertInvalid(()->validator.image(new MockMultipartFile("images","x.png","image/png",Arrays.copyOf(picture("png"),20))));
    }
    @Test void exactFiveMiBImageIsAcceptedButOneMoreByteIsRejected() throws Exception {
        byte[] png=Arrays.copyOf(picture("png"),ChatImageRequestValidator.MAX_BYTES);
        assertThat(validator.image(new MockMultipartFile("images","x.png","image/png",png)).sizeBytes()).isEqualTo(ChatImageRequestValidator.MAX_BYTES);
        assertInvalid(()->validator.image(new MockMultipartFile("images","x.png","image/png",Arrays.copyOf(png,png.length+1))));
    }
    @Test void oversizedDeclaredDimensionsAreRejectedBeforeAllocatingThePixelBuffer() throws Exception {
        byte[] png=picture("png");
        ByteBuffer.wrap(png).putInt(16,8000).putInt(20,5001);
        CRC32 crc=new CRC32();crc.update(png,12,17);ByteBuffer.wrap(png).putInt(29,(int)crc.getValue());
        assertInvalid(()->validator.image(new MockMultipartFile("images","large.png","image/png",png)));
    }
    @Test void validFortyMegapixelBoundaryIsDecodedWithBoundedSamplingAndStoredUnchanged() throws Exception {
        var bitmap=new BufferedImage(8000,5000,BufferedImage.TYPE_BYTE_BINARY);
        var out=new ByteArrayOutputStream();ImageIO.write(bitmap,"png",out);bitmap.flush();
        byte[] bytes=out.toByteArray();
        var image=validator.image(new MockMultipartFile("images","boundary.png","image/png",bytes));
        assertThat(image.bytes()).isEqualTo(bytes);assertThat(image.contentType()).isEqualTo("image/png");
    }
    @Test void multipartSupportsMetadataWithoutFilenameAndOneToFiveOrderedImages() throws Exception {
        for(int count:new int[]{1,5}){
            var request=request("{\"client_message_id\":\""+UUID.randomUUID()+"\"}",count);
            var command=validator.validate(request);
            assertThat(command.content()).isNull();assertThat(command.images()).hasSize(count);
        }
        assertInvalid(()->validator.validate(request("{\"client_message_id\":\""+UUID.randomUUID()+"\"}",0)));
        assertInvalid(()->validator.validate(request("{\"client_message_id\":\""+UUID.randomUUID()+"\"}",6)));
    }
    @Test void metadataRequiresCanonicalFieldsStrictJsonAndNonblankOptionalCaption() throws Exception {
        String id=UUID.randomUUID().toString();
        for(String metadata:new String[]{"{}","[]","{\"client_message_id\":null}","{\"client_message_id\":\"bad\"}",
                "{\"client_message_id\":\""+id+"\",\"sender_account_id\":\""+id+"\"}",
                "{\"client_message_id\":\""+id+"\",\"content\":\" \"}",
                "{\"client_message_id\":\""+id+"\",\"content\":3}",
                "{\"client_message_id\":\""+id+"\",\"client_message_id\":\""+id+"\"}",
                "{\"client_message_id\":\""+id+"\"} {}"}){
            assertInvalid(()->validator.validate(request(metadata,1)));
        }
        var caption=validator.validate(request("{\"client_message_id\":\""+id+"\",\"content\":\" xin chào 😀 \"}",1));
        assertThat(caption.content()).isEqualTo(" xin chào 😀 ");
    }
    @Test void unknownPartsDuplicateMetadataQueryAndBadMetadataMimeAreRejected() throws Exception {
        String metadata="{\"client_message_id\":\""+UUID.randomUUID()+"\"}";
        var unknown=request(metadata,1);unknown.addPart(new MockPart("owner","override".getBytes(StandardCharsets.UTF_8)));
        assertInvalid(()->validator.validate(unknown));
        var duplicate=request(metadata,1);duplicate.addPart(meta(metadata));
        assertInvalid(()->validator.validate(duplicate));
        var query=request(metadata,1);query.setQueryString("sender=other");
        assertInvalid(()->validator.validate(query));
        var badMime=new MockHttpServletRequest();badMime.addPart(new MockPart("message",metadata.getBytes(StandardCharsets.UTF_8)));
        badMime.addPart(image());assertInvalid(()->validator.validate(badMime));
    }
    @Test void imageModelDoesNotExposeMutableBytesOrPrivateBodyThroughToString() throws Exception {
        byte[] bytes=picture("png");var image=validator.image(new MockMultipartFile("images",bytes));
        byte[] copy=image.bytes();copy[0]=0;
        assertThat(image.bytes()[0]).isNotZero();
        assertThat(image.toString()).contains("REDACTED").doesNotContain(image.sha256());
    }
    MockHttpServletRequest request(String metadata,int count) throws Exception {
        var request=new MockHttpServletRequest();request.setMethod("POST");request.setContentType("multipart/form-data");
        request.addPart(meta(metadata));for(int i=0;i<count;i++)request.addPart(image());return request;
    }
    MockPart meta(String json){var part=new MockPart("message",json.getBytes(StandardCharsets.UTF_8));part.getHeaders().setContentType(MediaType.APPLICATION_JSON);return part;}
    MockPart image() throws Exception {var part=new MockPart("images","photo.png",picture("png"));part.getHeaders().setContentType(MediaType.IMAGE_PNG);return part;}
    static byte[] picture(String format) throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,out);return out.toByteArray();}
    interface Action{void run()throws Exception;}
    void assertInvalid(Action action){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiOperationException.class,e->assertThat(e.code()).isEqualTo("INVALID_REQUEST"));}
}
