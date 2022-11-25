import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.EC2;
import services.S3;
import services.SQS;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.model.Message;


import java.io.*;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class LocalApplication {
    private static final software.amazon.awssdk.regions.Region REGION = Region.US_EAST_1;
    private static final String LOCAL_TO_MANAGER_SQS_NAME = "localToManagerSQS";
    private static final String MANAGER_TO_LOCAL_SQS_NAME = "managerToLocalSQS" + new Date().getTime();
    private static final String SUMMARY_FILE_PATH = System.getProperty("user.dir") + "/src/summaryFile.txt";
    private static final String MANAGER_DATA = ""; // TODO!
    private static final Logger logger = LoggerFactory.getLogger(LocalApplication.class);
    private static final String MANAGER_NAME = "manager";
    private static final String pathPrefix = System.getProperty("user.dir") + "/src/";
    private static File outputFile;
    private static boolean terminate = false;
    private static final S3 s3 = new S3();
    private static final SQS localToManagerSQS = new SQS(LOCAL_TO_MANAGER_SQS_NAME);
    private static final SQS managerToLocalSQS = new SQS(MANAGER_TO_LOCAL_SQS_NAME);
    private static String doneMessage;
    private static final List<Message> messagesList = new LinkedList<Message>();


    private static void createManager() {
        Ec2Client ec2Client = Ec2Client.builder().region(REGION).build();
        if (isActive(ec2Client, MANAGER_NAME)) {
            // if the manager is already exist - request the url of the shared queue to the manager
            localToManagerSQS.requestQueueURL();
            System.out.println("Manager is already active");
        } else {
            // if the manager is not exist yet - create an instance in EC2 and a shared queue to the manager
            new EC2(MANAGER_NAME, 1, 1, MANAGER_DATA);
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
                    String name = !instance.tags().isEmpty() ? instance.tags().get(0).value() : "";
                    String state = instance.state().name().toString();
                    if (name.equals(nodeName) && (state.equals("pending") || state.equals("running"))) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Ec2Exception e) {
            logger.error("An error occurred while checking if " + nodeName + " is active.", e);
            return false;
        }
    }

    private static String uploadFileToS3(String inputFilePath) {
        s3.createBucket();
        return s3.putObject(inputFilePath, s3.getBucket(), "");
    }

    private static boolean isDoneMessage() {
        List<Message> messages = managerToLocalSQS.getMessages();
        if (!messages.isEmpty()) {
            for (Message m : messages) {
                messagesList.add(m);
                if (m.body().startsWith("done")) {
                    doneMessage = m.body().substring(5); //the message will be "done" +" "+ bucketName
                    return true;
                }
            }
        }
        return false;
    }

    public static void createHTMLSummaryFile(String bucketName) {
        if (bucketName.contains(" ")) {
            System.out.println("BucketName is missing.");
            System.exit(1);
        }
        String key = "SummaryFile.txt";
        try {
            File summaryFile = new File(SUMMARY_FILE_PATH);
            ResponseBytes<GetObjectResponse> responseBytes = s3.getObjectBytes(key, bucketName);
            byte[] objectData = responseBytes.asByteArray();
            OutputStream outputStream = new FileOutputStream(summaryFile);
            outputStream.write(objectData);
            outputStream.flush();
            outputStream.close();
            s3.deleteObject(key, bucketName);
            s3.deleteBucket(bucketName);

            BufferedReader bufferedReader = new BufferedReader(new FileReader(summaryFile));
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(outputFile));
            String headline = "<h2>Summary File</h2>" + "\n";
            bufferedWriter.write(headline);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.length() >= 7 && line.startsWith("http://")) {
                    bufferedWriter.write("<p><br /></p><img src=" + line + ">");
                }else  if (line.length() >= 26 && line.substring(0, 26).equals("Unable to open the picture")) {
                    bufferedWriter.write( "<p><br />" + line + "</p>");
                } else {
                    bufferedWriter.write("<p> " + line + "</p>");
                }
            }
            System.out.println("The summery file was created successfully.");

            bufferedWriter.close();
            bufferedReader.close();

            // delete the task from the queue from the manager to the local application.
            managerToLocalSQS.deleteMessages(messagesList);
            managerToLocalSQS.remove();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }


    public static void main(String[] args) {
        if (args.length < 3) {
            logger.error("Should be 3 arguments:  input file name, output file name and number of process files");
        } else {
            if (args.length == 4) {
                terminate = true;
            }
            String inputFilePath = pathPrefix + args[0];
            outputFile = new File(pathPrefix + args[1]);
            int numberOfFilesPerWorker = Integer.parseInt(args[2]);

            createManager();
            String bucketLocation = uploadFileToS3(inputFilePath);
            managerToLocalSQS.create();
            localToManagerSQS.send(
                    "Task " + MANAGER_TO_LOCAL_SQS_NAME + " " + numberOfFilesPerWorker + " " + bucketLocation + " " +
                            s3.getBucket());

            System.out.println("Sent message to manager, waiting for a response");
            while (!isDoneMessage()) {
            }
            System.out.println("A response was received from the manager.");
            createHTMLSummaryFile(doneMessage);

            if (terminate)
                localToManagerSQS.send("Terminate");
        }
    }
}
