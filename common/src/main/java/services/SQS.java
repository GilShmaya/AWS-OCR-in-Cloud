package services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.HashMap;
import java.util.Map;

public class SQS {
    private static Logger logger = LoggerFactory.getLogger(SQS.class);
    private final Region REGION = Region.US_EAST_1; // TODO: check the region
    private String queueName;
    private SqsClient sqsClient;
    private Map<QueueAttributeName,String> attributes;
    private String url;

    public SQS(){
        this.sqsClient = SqsClient.builder().region(REGION).build();
        this.attributes = new HashMap<>();
        attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, "60");
    }

    public void create(String queueName) {
        if(isActive()) {
            logger.error("The SQS is already active");
        }
        this.queueName = queueName;
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .attributes(attributes)
                .build();

        sqsClient.createQueue(createQueueRequest);
        getURL();
    }

    private boolean isActive(){
        return url != null;
    }

    public String getURL() {
        if(url == null){
            GetQueueUrlRequest req= GetQueueUrlRequest.builder().queueName(queueName).build();
            url = sqsClient.getQueueUrl(req).queueUrl();
        }
        return url;
    }


}
