package com.stylemind.verification;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;
import org.testcontainers.containers.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.checksums.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;

/** Explicit live verification only. Owns exact test keys, never changes bucket configuration. */
final class BackblazeRuntimeStorage implements AutoCloseable {
    static final List<String> VARIABLES=List.of("STYLEMIND_CHAT_IMAGES_ENABLED","STYLEMIND_CHAT_IMAGES_BUCKET",
            "STYLEMIND_CHAT_IMAGES_REGION","STYLEMIND_CHAT_IMAGES_ENDPOINT","AWS_ACCESS_KEY_ID","AWS_SECRET_ACCESS_KEY");
    final String bucket="stylemind-chat";
    final String endpoint="https://s3.us-east-005.backblazeb2.com";
    final Set<String> ownedKeys=new LinkedHashSet<>();
    final S3Client s3;
    int privateReads;
    int deletedVersions;

    BackblazeRuntimeStorage() {
        check("true".equals(System.getenv("EP06_LIVE_BACKBLAZE")),"Live cloud mode must be explicit");
        check(bucket.equals(System.getenv("STYLEMIND_CHAT_IMAGES_BUCKET")) &&
                endpoint.equals(System.getenv("STYLEMIND_CHAT_IMAGES_ENDPOINT")) &&
                "us-east-005".equals(System.getenv("STYLEMIND_CHAT_IMAGES_REGION")),"Unexpected live test target");
        s3=S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of("us-east-005"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        required("AWS_ACCESS_KEY_ID"),required("AWS_SECRET_ACCESS_KEY"))))
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(15)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(config->config.apiCallTimeout(Duration.ofSeconds(40)).apiCallAttemptTimeout(Duration.ofSeconds(20)))
                .build();
    }
    Map<String,String> environment() {
        var values=new HashMap<String,String>();
        for(String name:VARIABLES)values.put(name,required(name));
        return values;
    }
    void probe() {
        String key="chat/v1/verification/"+UUID.randomUUID()+".png";
        ownedKeys.add(key); // Record before PUT, including uncertain outcomes.
        try {
            var output=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);
            byte[] png=output.toByteArray();
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/png").build(),RequestBody.fromBytes(png));
            byte[] restored=s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
            check(MessageDigest.isEqual(png,restored),"Cloud probe preserved image bytes");
            assertPrivate(key);
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            try {
                s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
                throw new AssertionError("Deleted probe must not be readable by its current key");
            } catch(S3Exception expected) { check(expected.statusCode()==404,"Delete marker hides the probe"); }
            System.out.println("EP06_BACKBLAZE_PUT_GET_ANONYMOUS_DENIED_DELETE_MARKER_PASSED");
        } catch(AssertionError expected) { throw expected; }
        catch(Exception failed) { throw sanitized("cloud probe",failed); }
    }
    void collect(PostgreSQLContainer<?> database) {
        if(!database.isRunning())return;
        try(var connection=DriverManager.getConnection(database.getJdbcUrl(),database.getUsername(),database.getPassword());
            var statement=connection.createStatement();
            var rows=statement.executeQuery("SELECT storage_object_key FROM public.chat_attachment")) {
            while(rows.next()) {
                String key=rows.getString(1);
                check(key.matches("chat/v1/[0-9a-f-]{36}/[1-5]-[0-9a-f]{64}\\.(jpg|png|webp)"),"Only generated image keys from the owned test DB may be cleaned up");
                ownedKeys.add(key);
            }
        } catch(AssertionError expected) { throw expected; }
        catch(Exception failed) { throw sanitized("collect test DB image keys",failed); }
    }
    void verifyReferencedObjectsPrivate(PostgreSQLContainer<?> database) {
        collect(database);
        for(String key:ownedKeys) {
            if(key.startsWith("chat/v1/verification/"))continue;
            try {
                check(s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength()>0,"Referenced image exists in cloud");
                assertPrivate(key);
            } catch(AssertionError expected) { throw expected; }
            catch(Exception failed) { throw sanitized("verify private referenced image",failed); }
        }
    }
    void assertPrivate(String key)throws Exception {
        var response=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build()
                .send(HttpRequest.newBuilder(URI.create(endpoint+"/"+bucket+"/"+key)).timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
        check(Set.of(401,403,404).contains(response.statusCode()),"Anonymous access must be denied for an existing image");
        privateReads++;
    }
    String currentVersion(PostgreSQLContainer<?> database,String attachment) {
        try(var connection=DriverManager.getConnection(database.getJdbcUrl(),database.getUsername(),database.getPassword());
            var query=connection.prepareStatement("SELECT storage_object_key FROM public.chat_attachment WHERE chat_attachment_id=?")) {
            query.setObject(1,UUID.fromString(attachment));
            try(var rows=query.executeQuery()) {
                check(rows.next(),"Image metadata exists in test DB");
                String key=rows.getString(1);ownedKeys.add(key);
                String version=s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).versionId();
                check(version!=null&&!version.isBlank(),"Live provider returns an image version");
                return version;
            }
        } catch(AssertionError expected) {throw expected;}
        catch(Exception failed) {throw sanitized("inspect image retry version",failed);}
    }
    void cleanup() {
        try {
            java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("ep06.evidence"),"backblaze-owned-test-keys.txt"),ownedKeys);
        } catch(java.io.IOException failed) {throw new AssertionError("Could not save test-key cleanup manifest; no credentials included");}
        int failed=0;
        for(String key:ownedKeys) {
            try {
                // Bucket keeps all versions. Removing only the current key would leave stored data.
                for(var page:s3.listObjectVersionsPaginator(ListObjectVersionsRequest.builder().bucket(bucket).prefix(key).build())) {
                    check(!page.isTruncated(),"Unexpected number of versions for a unique test image");
                    for(var version:page.versions())if(key.equals(version.key())) {
                        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).versionId(version.versionId()).build());deletedVersions++;
                    }
                    for(var marker:page.deleteMarkers())if(key.equals(marker.key())) {
                        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).versionId(marker.versionId()).build());deletedVersions++;
                    }
                }
                var remaining=s3.listObjectVersions(ListObjectVersionsRequest.builder().bucket(bucket).prefix(key).build());
                check(remaining.versions().stream().noneMatch(v->key.equals(v.key())) &&
                        remaining.deleteMarkers().stream().noneMatch(v->key.equals(v.key())),"Test image versions were fully removed");
            } catch(Exception | AssertionError error) { failed++; }
        }
        System.out.println("EP06_BACKBLAZE_CLEANUP keys="+ownedKeys.size()+" versions_deleted="+deletedVersions+" anonymous_denials="+privateReads+" failed_keys="+failed);
        check(failed==0,"Cloud cleanup incomplete; do not treat this run as pass");
    }
    static AssertionError sanitized(String operation,Exception failed) {
        return new AssertionError(operation+" failed"+(failed instanceof S3Exception error?" (HTTP "+error.statusCode()+")":"")+"; provider details and credentials omitted");
    }
    static String required(String name) {String value=System.getenv(name);check(value!=null&&!value.isBlank(),"Missing live image variable: "+name);return value;}
    static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    @Override public void close(){s3.close();}
    @Override public String toString(){return "BackblazeRuntimeStorage[credentials=REDACTED]";}
}
