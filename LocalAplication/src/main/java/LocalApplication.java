import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.EC2;
import services.S3;
import services.SQS;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Date;

public class LocalApplication {
    private static String LOCAL_TO_MANAGER_SQS_NAME = "localToManagerSQS";
    private static String MANAGER_TO_LOCAL_SQS_NAME = "managerToLocalSQS" + new Date().getTime();;
    private static Logger logger = LoggerFactory.getLogger(LocalApplication.class);
    private static String MANAGER_NAME = "manager";
    private static String pathPrefix = System.getProperty("user.dir") + "/src/";
    private static String inputFilePath;
    private static String outputFilePath;
    private static int numberOfFilesPerWorker;
    private static boolean terminate = false;
    private static S3 s3 = new S3();
    private static SQS localToManagerSQS = new SQS(LOCAL_TO_MANAGER_SQS_NAME);
    private static SQS managerToLocalSQS = new SQS(MANAGER_TO_LOCAL_SQS_NAME);;

    private static void createManager() {
        Ec2Client ec2Client = Ec2Client.builder().build();
        if(isActive(ec2Client, MANAGER_NAME)){
            // if the manager is already exist - request the url of the shared queue to the manager
            localToManagerSQS.requestQueueURL();
            logger.info("Manager is already active");
        } else {
            // if the manager is not exist yet - create an instance in EC2 and a shared queue to the manager
            new EC2(MANAGER_NAME,);
            localToManagerSQS.create();
        }
    }

    // Checks if the node with the name <nodeName> is active on the EC2 cloud.
    private static boolean isActive(Ec2Client ec2Client, String nodeName) {
        try {
            DescribeInstancesResponse response =
                    ec2Client.describeInstances(DescribeInstancesRequest.builder().build()); // request EC2 instances
            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    String name = instance.tags().get(0).value();
                    String state = instance.state().name().toString();
                    if (name.equals(nodeName) && (state.equals("pending") || state.equals("running"))) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Ec2Exception e) {
            logger.error("An error occurred while checking if " + nodeName + " is active.",
                    e.awsErrorDetails().errorMessage());
            return false;
        }
    }

    private static String uploadFileToS3(String inputFilePath) {
        s3.createBucket();
        return s3.put(inputFilePath, s3.getBucket());
    }

    private static void sendMessage() {

    }



    public static void main(String[] args) {
        if(args.length < 3){ // TODO: should be 4? in case of  an optional argument terminate?
            logger.error("Should be 3 arguments:  input file name, output file name and number of process files");
        } else {
            if(args.length == 4){
                terminate = true; // TODO: which value should be here? should we check this?
            }
            inputFilePath = pathPrefix + args[0];
            outputFilePath = pathPrefix + args[1];
            numberOfFilesPerWorker = Integer.parseInt(args[2]);

            createManager();
            String bucketLocation = uploadFileToS3(inputFilePath);
            managerToLocalSQS.create();
            managerToLocalSQS.send("Task " + MANAGER_TO_LOCAL_SQS_NAME + " " + numberOfFilesPerWorker + " " + bucketLocation + " " + s3.getBucket());



        }
    }
}
