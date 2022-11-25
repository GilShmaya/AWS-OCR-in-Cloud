
import com.asprise.ocr.*;

import services.DataBase;
import services.SQS;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Worker {

    public static String processOCR(URL[] url) throws IOException {
        try {
            Ocr.setUp();
            Ocr ocr = new Ocr();
            ocr.startEngine("eng", Ocr.SPEED_FASTEST);
            String imageText = ocr.recognize(url, Ocr.RECOGNIZE_TYPE_TEXT, Ocr.OUTPUT_FORMAT_PLAINTEXT);
            return imageText;
        } catch (OcrException e) {
            return "Can't open the picture";
        }
    }

    public static void main(String[] args) throws IOException {
        DataBase dataBase = DataBase.getInstance();

        SQS managerGetFromWorkers =
                new SQS("workersToManagerSQS"); // SQS for the messages the workers send the manager.
        managerGetFromWorkers.requestQueueURL();
        SQS workersGetFromManager = new SQS("managerToWorkersSQS"); // SQS for the tasks the manager send to the workers
        workersGetFromManager.requestQueueURL();
        List<Message> msg = new LinkedList<>();

        while (!dataBase.isTerminate()) {
            System.out.println("--- Start Working ---\n");
            // The worker gets a message from an SQS queue
            try {
                msg = workersGetFromManager.getMessages();
            } catch (QueueDoesNotExistException e){
                if (dataBase.isTerminate())
                    return;
            }
            if (!msg.isEmpty()) { // (Task_key, count, l[0], bucket, LocalQueue)
                String task = msg.get(0).body();
                String[] split = task.split(" ");
                System.out.println(split);
                if (split.length >= 3) { // all necessary information is available.
                    String taskName = split[0];
                    String url = split[1];
                    String bucket = split[2];
                    String q = split[3];

                    // Downloads the image indicated in the message & performs OCR on the image
                    String res = "";

                    try {
                        res = processOCR(new URL[]{new URL(url)});
                    } catch (Exception e) {
                        System.out.println("cant open url " + url + " got error: " + e);
                        url = "invalid url";
                        res = "Can't open the picture";
                    }
                    System.out.println("Done working on url: " + url);

                    // Notify the manager of the text associated with that image
                    String summaryMsg = "finish " + taskName + " " + bucket + " " + q + " " + url + " " + res;
                    managerGetFromWorkers.send(summaryMsg);
                    workersGetFromManager.deleteMessages(msg); // Remove the processed message from the SQS queue
                }
            } else {
                System.out.println("Waiting for a task");
            }
        }
    }
}