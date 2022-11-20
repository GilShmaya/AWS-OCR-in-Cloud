
import com.asprise.ocr.*;

import services.SQS;
import software.amazon.awssdk.services.sqs.model.Message;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Worker {

    public static String processOCR(URL[] url ) throws IOException {
        Ocr.setUp();
        Ocr ocr = new Ocr();
        ocr.startEngine("eng", Ocr.SPEED_FASTEST);
        String imageText = ocr.recognize(url, Ocr.RECOGNIZE_TYPE_TEXT, Ocr.OUTPUT_FORMAT_PLAINTEXT);
        return imageText;
    }

    public static void main(String[] args) throws IOException {
        boolean doTerminate = false;

        SQS managerGetFromWorkers = new SQS("workersToManagerQ"); // SQS for the messages the workers send the manager.
        managerGetFromWorkers.requestQueueURL();
        SQS workersGetFromManager = new SQS("managerToWorkersQ"); // SQS for the tasks the manager send to the workers
        workersGetFromManager.requestQueueURL();

        while (!doTerminate) {
            System.out.println("--- Start Working ---\n");
            // The worker gets a message from an SQS queue
            List<Message> msg = workersGetFromManager.getMessages();
            if (!msg.isEmpty()) { // (Task_key, count, l[0], bucket, LocalQueue)
                // todo : what is the l[0] above?
                String task = msg.get(0).body();
                String[] split = task.split(" ");
                System.out.println(split);
                if (split.length >= 3) { // all necessary information is available.
                    String name = split[0];
                    String url = split[1];
                    String bucket = split[2];
                    String q = split[3];

                    // Downloads the image indicated in the message & performs OCR on the image
                    String res = processOCR(new URL[]{new URL(url)});

                    // Notify the manager of the text associated with that image
                    String summaryMsg = "Finish " + name + " " + bucket + " " + q + " " + url + " " + res;
                    managerGetFromWorkers.send(summaryMsg);
                    workersGetFromManager.deleteMessages(msg); // Remove the processed message from the SQS queue
                }
            } else {
                System.out.println("Missing Information in the message => worker can't do his job");
            }
        }
    }
}