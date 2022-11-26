import services.DataBase;
import services.SQS;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;
import java.util.List;

public class ManagerWorkersContact implements Runnable {
    private DataBase dataBase;
    private SQS workerToManagerQ;

    public ManagerWorkersContact(SQS q){
        dataBase = DataBase.getInstance();
        workerToManagerQ =q;
    }

    public void processText (String key, String thisURL, String bucket, String localSQS, Message msg) throws IOException {
        int location =7+key.length()+1 +thisURL.length()+1+bucket.length()+1+localSQS.length()+1;
        String imgText = msg.body().substring(location);
        String result = "";
        if(!(imgText.startsWith("Can't open the picture"))){
            result = thisURL+"\n"+imgText+"\n";
        }
        else {
            result = imgText+"\n";
        }
        boolean isDone = dataBase.handleOneResult(result, bucket);
        if (isDone){
            dataBase.removeTask("OCRFile.txt", bucket);
            SQS ans= new SQS(localSQS);
            ans.requestQueueURL();
            ans.send("done " + bucket);
        }
    }

    public void readMsg() throws IOException {
        List<Message> nextMsg= workerToManagerQ.getMessages();
        if (!nextMsg.isEmpty()){
            for (Message msg : nextMsg) {
                if (msg.body().startsWith("finish")){
                    System.out.println("--- A new message from the worker is waiting ---");
                    String [] msgToString = msg.body().split(" ");

                    if(msgToString.length >= 5) { //message contain all necessary information
                        String key= msgToString[1];
                        System.out.println("Got a result from worker regarding task: "+key+" \n");
                        String bucket = msgToString[2];
                        String localSQS= msgToString[3];
                        String thisURL =msgToString[4];

                        // processing the text that needs to be written in the summary file
                        // & send it to 'SetTaskImg' function, together with the local app's bucket.
                        processText(key, thisURL, bucket, localSQS, msg);
                    }
                    else {
                        System.out.println("missing necessary information");
                        System.exit(1);
                    }
                }
            }
            // Remove the processed message from the SQS queue
            workerToManagerQ.deleteMessages(nextMsg);
        }
    }

    public void run() {
        try {
            workerToManagerQ = new SQS("workersToManagerSQS");
            workerToManagerQ.requestQueueURL();
            while(!dataBase.isTerminate()) {
                readMsg();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

}