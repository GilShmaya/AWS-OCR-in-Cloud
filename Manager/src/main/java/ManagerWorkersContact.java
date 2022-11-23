import services.DataBase;
import services.SQS;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.IOException;
import java.util.List;

public class ManagerWorkersContact implements Runnable {
    private DataBase dataBase;
    private boolean finish;
    private SQS workersToManager;

    public ManagerWorkersContact(SQS q){
        dataBase = DataBase.getInstance();
        finish=false;
        workersToManager=q;
    }

    public void processText (String key, String thisURL, String bucket, String localSQS, Message msg) throws IOException {
        int location =7+key.length()+1 +thisURL.length()+1+bucket.length()+1+localSQS.length()+1;
        String imgText= msg.body().substring(location);
        String result ="";
        if(!(imgText.substring(0,26).equals("Can't open the picture"))){ // todo : check why always true?
            result =thisURL+"\n"+imgText+"\n";
        }
        else {
            result =imgText+"\n";
        }
        boolean isDone = dataBase.handleOneResult(result, bucket);
        if (isDone){
            dataBase.removeTask("OCRFile.txt", bucket);
            SQS ans= new SQS(localSQS);
            ans.requestQueueURL();
            ans.send("done "+bucket);
        }
    }

    public void readMsg() throws IOException {
        List<Message> nextMsg= workersToManager.getMessages();
        if (!nextMsg.isEmpty()){
            for (Message msg : nextMsg) {
                System.out.println("--- A new message from the manager to the workers is waiting ---");
                if ((msg.body().substring(0,6)).equals("Finish")){
                    String [] msgToString = msg.body().substring(7).split(" ");
                    if(msgToString.length > 3) { //message contain all necessary information
                        String key= msgToString[0];
                        System.out.println("The Task: "+key+"\n");
                        String bucket = msgToString[2];
                        String localSQS= msgToString[3];
                        String thisURL =msgToString[1];

                        // processing the text that needs to be written in the summary file
                        // & send it to 'processText' function, together with the local app's bucket.
                        processText(key, thisURL, bucket, localSQS, msg);
                    }
                    else {
                        System.out.println("missing necessary information");
                        System.exit(1);
                    }
                }
            }
            // Remove the processed message from the SQS queue
            workersToManager.deleteMessages(nextMsg);
        }
    }

    public void run() {
        System.out.println("am i even running?");
        try {
            workersToManager = new SQS("workersToManager");
            workersToManager.requestQueueURL();
            while(!finish) { // Until a termination message is sent by the manager
                readMsg();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

}