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
    private static final Region REGION = Region.US_EAST_1; // TODO: check the region
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

    public String putObject(String path, String bucket) {
        String key = "";
        try {
            key = "key" + System.currentTimeMillis();
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

    private static void multipartUpload(String bucketName, String key) {
        // First create a multipart upload and get the upload id
        CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(createMultipartUploadRequest);
        String uploadId = response.uploadId();
        System.out.println(uploadId);

        // Upload all the different parts of the object
        UploadPartRequest uploadPartRequest1 = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .partNumber(1).build();

        String etag1 =
                s3Client.uploadPart(uploadPartRequest1, RequestBody.fromByteBuffer(getRandomByteBuffer(5 * mB))).eTag();

        CompletedPart part1 = CompletedPart.builder().partNumber(1).eTag(etag1).build();

        UploadPartRequest uploadPartRequest2 = UploadPartRequest.builder().bucket(bucketName).key(key)
                .uploadId(uploadId)
                .partNumber(2).build();
        String etag2 =
                s3Client.uploadPart(uploadPartRequest2, RequestBody.fromByteBuffer(getRandomByteBuffer(3 * mB))).eTag();
        CompletedPart part2 = CompletedPart.builder().partNumber(2).eTag(etag2).build();

        // Finally call completeMultipartUpload operation to tell S3 to merge all uploaded
        // parts and finish the multipart operation.
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(part1, part2)
                .build();

        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                CompleteMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .uploadId(uploadId)
                        .multipartUpload(completedMultipartUpload)
                        .build();

        s3Client.completeMultipartUpload(completeMultipartUploadRequest);
    }

    public void deleteObject(String key, String bucketName) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }

    public void deleteObject(String key) {
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
