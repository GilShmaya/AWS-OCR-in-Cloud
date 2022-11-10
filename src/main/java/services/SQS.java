package services;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

public class SQS {
    private final Region REGION = Region.US_EAST_1; // TODO: check
    private final String queueName;
    private SqsClient sqsClient;

    public SQS(String queueName){
        this.queueName = queueName;
        this.sqsClient = SqsClient.builder().region(REGION).build();

    }
    private void create(String queueName) {
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .build();

        sqsClient.createQueue(createQueueRequest);
    }

    public void getURL() {
    }


}
