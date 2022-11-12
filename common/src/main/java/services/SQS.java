package services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQS {
    private static final Region REGION = Region.US_EAST_1;
    private static final int DELAY_SECONDS = 5;
    private static final int WAIT_TIME_SECOND = 20;
    private static final Logger logger = LoggerFactory.getLogger(SQS.class);
    private final String queueName;
    private final SqsClient sqsClient;
    private final Map<QueueAttributeName, String> attributes;
    private String url;

    public SQS(String queueName) {
        this.queueName = queueName;
        this.sqsClient = SqsClient.builder().region(REGION).build();
        this.attributes = new HashMap<>();
        attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, "60");
    }

    public void create() {
        if (isActive()) {
            logger.error("The SQS is already active");
        }
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .attributes(attributes)
                .build();

        sqsClient.createQueue(createQueueRequest);
        requestQueueURL();
        System.out.printf("Successfully started SQS Instance with url %s", url);
    }

    public void remove(){
        if ( isActive()) {
            DeleteQueueRequest request = DeleteQueueRequest.builder()
                    .queueUrl(url)
                    .build();
            sqsClient.deleteQueue(request);
        }
    }

    private boolean isActive() {
        return url != null;
    }

    public String requestQueueURL() {
        if (url == null) {
            GetQueueUrlRequest request = GetQueueUrlRequest.builder().queueName(queueName).build();
            url = sqsClient.getQueueUrl(request).queueUrl();
        }
        return url;
    }

    public void send(String message) {
        SendMessageRequest request = SendMessageRequest.builder()
                .delaySeconds(DELAY_SECONDS)
                .queueUrl(url)
                .messageBody(message)
                .build();
        sqsClient.sendMessage(request);
    }

    public List<Message> getMessages(){
        ReceiveMessageRequest request = ReceiveMessageRequest
                .builder()
                .queueUrl(url)
                .waitTimeSeconds(WAIT_TIME_SECOND)
                .maxNumberOfMessages(1) // one message each time
                .build();
        return sqsClient.receiveMessage(request).messages();
    }

    public void deleteMessages(List<Message> messages){
        for (Message m : messages) {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(url)
                    .receiptHandle(m.receiptHandle())
                    .build();
            sqsClient.deleteMessage(deleteRequest);
        }
    }
}
