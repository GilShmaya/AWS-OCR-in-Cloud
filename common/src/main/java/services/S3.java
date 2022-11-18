package services;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Random;

public class S3 {
    private static final int  mB = 1024 * 1024;
    private static final Region REGION = Region.US_EAST_1;
    private static S3Client s3Client;
    private static String bucketName;

    public S3() {
        this.s3Client = S3Client.builder().region(REGION).build();
        this.bucketName = "bucket" + System.currentTimeMillis();
    }

    private static ByteBuffer getRandomByteBuffer(int size) {
        byte[] bytes = new byte[size];
        new Random().nextBytes(bytes);
        return ByteBuffer.wrap(bytes);
    }

    public String getBucket() {
        return this.bucketName;
    }

    public void createBucket() {
        s3Client.createBucket(CreateBucketRequest
                .builder()
                .bucket(bucketName)
                .acl(BucketCannedACL.PUBLIC_READ)
                .createBucketConfiguration(CreateBucketConfiguration.builder().build())
                .build());
        System.out.printf("Successfully created a bucket Instance in S3 with name %s", bucketName);
    }

    public void deleteBucket(String bucketName) {
        DeleteBucketRequest request = DeleteBucketRequest.builder()
                .bucket(bucketName)
                .build();
        s3Client.deleteBucket(request);
    }

    public String putObject(String path, String bucket, String key) {
        try {
            if (key == "") {
                key = "key" + System.currentTimeMillis();
            }
            PutObjectResponse response = s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build(),
                    Paths.get(path));
        } catch (S3Exception e) {
            System.err.println(e.getMessage());
        }
        return key;
    }


    public void deleteObject(String key, String bucketName) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }


    public synchronized ResponseBytes<GetObjectResponse> getObjectBytes(String key, String bucketName) {
        GetObjectRequest request = GetObjectRequest.builder()
                .key(key)
                .bucket(bucketName)
                .build();
        return s3Client.getObjectAsBytes(request);
    }
}
