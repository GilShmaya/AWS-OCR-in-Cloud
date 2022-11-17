import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import services.SQS;
import software.amazon.awssdk.services.sqs.model.Message;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class worker { //todo:  when finished a task return "final"+t ???

    public static String processOCR (String url) throws IOException {
        String ans;
        Tesseract instance = new Tesseract();
        instance.setDatapath("./tessdata");
        try {
            ans = instance.doOCR(ImageIO.read(new URL(url).openStream()));
        } catch (TesseractException | IOException e) {
            ans = "Unable to open the picture\n input file: "+ url+" \n" + /////TODO
                    "A short description of the exception: "+ e.getMessage()+ "\n";
            e.printStackTrace();
        }
        return ans;
    }
    public static void main(String[] args) throws IOException {
        boolean doTerminate = false;

        SQS managerGetFromWorkers  = new SQS("WorkersManagerSQS"); // SQS for the messages the workers send the manager.
        managerGetFromWorkers.requestQueueURL();
        SQS workersGetFromManager  = new SQS("ManagerWorkersSQS"); // SQS for the tasks the manager send to the workers
        workersGetFromManager.requestQueueURL();

        while (!doTerminate) {
            System.out.println("***** Start Working ******\n"); // todo
            //reading the image message that the manager sent in the queue
            List<Message> msg = workersGetFromManager.getMessages();
            if (!msg.isEmpty()) { // (Task_key, count, l[0], bucket, LocalQueue)
                // todo : what is the l[0] above?
                String task = msg.get(0).body();
                String[] split = task.split(" ");
                if (split.length >= 3) { // all the necessary information is available.
                    String name = split[0];
                    String url = split[1];
                    String bucket= split[2];
                    String q= split[3];
                    String res = processOCR (url); // OCR on the image

                    // after finish processing the image, the worker create an appropriate message to the manager & send it to him
                    String summaryMsg = "Finish " + name + " "+ bucket+ " " +q+ " " + url + " " + res;
                    managerGetFromWorkers.send(summaryMsg);
                    workersGetFromManager.deleteMessages(msg); // deleting the message after finish the job
                }
            } else {
                System.out.println("Missing Information in the message => worker can't do his job");
            }
        }
    }
}