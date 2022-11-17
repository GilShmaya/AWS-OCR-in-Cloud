import services.DataBase;
import services.EC2;
import services.S3;
import services.SQS;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.*;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AppManagerContact implements Runnable {
    private SQS ManagerAndAppQ;
    private SQS ManagerAndWorkersQ;
    private S3 s3;
    private DataBase DB;
    private boolean terminate;
    private int count;
    private Thread WorkerActionThread;

    public AppManagerContact(S3 s, SQS MW){
        this.ManagerAndWorkersQ= MW;
        s3=s;
        DB= DataBase.getInstance();
        terminate=false;
        count= 0;
    }

    private int workersForTask (int countMsg, int n){
        return ((int) (Math.ceil((double) countMsg / n)-DB.getWorkersAmount()));
    }

    // splits the main task to messages (according to the lines),
    // sends the messages to the workers queue & count the number of messages
    public int splitTask (File InputFile, String Task_key, String bucket, String LocalQueue) throws FileNotFoundException {
        int counter=0;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(InputFile)); // read the file
            String line = reader.readLine();
            while (line != null){
                String [] Line= line.split("\n"); //TODO:check!
                //example: Task_24.11.20..._Queue_1 http:... bucket24.11...
                String msg = Task_key+"_"+counter+" "+Line[0]+" "+bucket+ " "+ LocalQueue;
                ManagerAndWorkersQ.send(msg);
                line = reader.readLine();
                counter++;
            }
            reader.close();
        }
        catch (IOException e){
            System.out.println("Problem in function: splitTask"); //TODO: check if necessary
            e.printStackTrace();
        }
        return counter;
    }

    //count number of urls
    public Integer numOfURLs (File InputFile){
        int counter= 0;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(InputFile));
            String line = reader.readLine();
            while (line != null){
                line = reader.readLine();
                counter++;
            }
            reader.close();
        }
        catch (IOException e){
            System.out.println("Problem in function: numOfURLs"); //TODO: check if necessary
            e.printStackTrace();
        }
        return counter;
    }


    public File fileFromS3 (String name, String key, String bucket) throws IOException {
        ResponseBytes<GetObjectResponse> response= s3.getObjectBytes(key,bucket);
        byte [] objectData= response.asByteArray();
        String path= System.getProperty("user.dir")+"/"+name+".txt";
        File Input= new File(path);
        OutputStream outputStream= new FileOutputStream(Input);
        outputStream.write(objectData);
        outputStream.flush();
        outputStream.close();
        return Input;
    }


    public void resFile (String name, String bucket) throws IOException {
        String ansFilePath= System.getProperty("user.dir")+"/SummaryFile.txt";
        File ansFile= new File (ansFilePath);
        OutputStream outputStream= new FileOutputStream(ansFile);
        byte [] data= "nothing yet".getBytes();
        outputStream.write(data);
        outputStream.flush();
        outputStream.close();
        System.out.println("*** put the not really empty summary file in S3 ***\n"); //TODO : WHAT???
        s3.putObject(ansFilePath, bucket, "SummaryFile.txt");
        System.out.println("**** Add Task: "+name+" *****"); //TODO: CHECK IF NECESSARY
        ansFile.delete(); //delete the file after we finished with it (scalability)
    }



    public void OCRfile(File input, String bucket) throws IOException {
        Integer NumOfURL= numOfURLs(input);
        String PathOCR= System.getProperty("user.dir")+"/OCRFile.txt";
        File OCR= new File (PathOCR);
        OutputStream outputStreamOCR= new FileOutputStream(OCR);
        System.out.println("*** UrlNum: "+ NumOfURL.toString()+" ***"); //TODO : check if necessary
        byte [] urls= NumOfURL.toString().getBytes();
        outputStreamOCR.write(urls);
        outputStreamOCR.flush();
        outputStreamOCR.close();
        System.out.println("*** put the OCRfile with the number of URLs in S3 ***\n"); //TODO : check if necessary
        s3.putObject(PathOCR, bucket, "OCRFile.txt");
        OCR.delete(); //delete the file after we finished with it (scalability)
    }

    // delete the task from s3 after reading and analyzed it
    // delete the inputFile we got from s3
    // delete the message from ManagerAndAppQ after sending it to the workers
    public void finishWithTask (String key, String name, String bucket, Message msg, File input) {
        s3.deleteObject(key,bucket);
        System.out.println("**** Deleting the task: "+ name+ " Key: "+key+ " from bucket: "+ bucket +" ****");
        input.delete();
        DB.addTask();
        List<Message> delete= new LinkedList<Message>();
        delete.add(msg);
        ManagerAndAppQ.deleteMessages(delete);
    }


    public void NewMsgFromApp (Message msg) throws IOException {
        if(!terminate) {
            System.out.println("***** IN NEW TASK *****\n"); //TODO: check if necessary

            //get the information about the new message
            String str= msg.body().substring(8); //FileKey + " " + n + " " + s3.getBucket() + " " + QueueName
            String[] splitted = str.split(" ");
            if (splitted.length != 4) {
                System.out.println("The Data should contain file key, n, bucket name, queue");
                System.exit(1);
            }
            String TaskName = "Task" + new Date().getTime(); //TODO: what does the getTime means?
            String key = splitted[0];
            int n = Integer.parseInt(splitted[1]);
            String bucket= splitted[2];
            String LocalQueue= splitted[3];

            File ourFile = fileFromS3(TaskName, key, bucket); //get the inputFile from s3
            OCRfile(ourFile, bucket); // creating an OCR file with the number of urls in the input message & sending it to s3
            resFile(TaskName, bucket); // creating an empty file for the worker's results

            //activate the workers for this task
            System.out.println("*** now activate the workers if needed *** \n"); //TODO : CHECK IF NECESSARY
            int numOfWorkers = workersForTask(numOfURLs(ourFile),n);
            DB.addAmountOfWorkers(numOfWorkers);

            // starting the thread only if it's the beginning of the process //TODO : CHECK IF true
            if (count==0){
                count++;
                WorkerActionThread.start();
            }
            System.out.println("*** send the tasks to the workers *** \n"); //TODO : CHECK IF NECESSARY
            Integer splittingTasks = splitTask(ourFile, TaskName, bucket, LocalQueue);
            finishWithTask(key, TaskName, bucket, msg, ourFile);
        }
    }



    public void CheckingMsgFromApp() throws InterruptedException, IOException {
        List<Message> messages= ManagerAndAppQ.getMessages();
        if (!messages.isEmpty()){
            System.out.println("*** A new Message is waiting in ManagerAndAppQ ***");
            for (Message msg : messages){
                System.out.println("*** The message arrived from app is: "+msg.body()+" ***\n"); //todo : is it necessary?
                String shouldTerminate = msg.body().substring(0,9);
                String task = msg.body().substring(0,7);
                if(shouldTerminate.equals("Terminate")) {
                    Terminate();
                    return;
                }
                else if(task.equals("NewTask")){
                    NewMsgFromApp(msg);
                }
            }
        }
    }


    public void terminateWorkers () {
        EC2 worker = DB.assignWorker();
        while(worker!= null){
            System.out.println("*** Terminates 1 worker ***"); // TODO: necessary?
            worker.terminate();
            worker = DB.assignWorker();
        }
    }

    public void Terminate() throws InterruptedException {
        terminate=true;
        // System.out.println("*** Waiting for all tasks to finish ***"); // TODO: necessary?
        while(DB.getTasksAmount()>0){ // there are more tasks to process
            try {
                TimeUnit.SECONDS.sleep(45); // todo : why 45?
            } catch (InterruptedException exception) {
                exception.printStackTrace();
            }
        }
        System.out.println("*** All tasks are finished ***"); // TODO: necessary?
        WorkerActionThread.interrupt();
        terminateWorkers();
//////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // 4. terminate the locals to manager sqs
        ManagerAndAppQ.remove();
        ManagerAndWorkersQ.remove();
        SQS WorkersManagerSQS = new SQS("WorkersManagerSQS");
        WorkersManagerSQS.requestQueueURL();
        WorkersManagerSQS.remove();
        EC2 ec2= new EC2();
        try {
            DescribeInstancesRequest req = DescribeInstancesRequest.builder().build();
            DescribeInstancesResponse res = ec2.getEC2Client().describeInstances(req);
            for (Reservation reservation : res.reservations()) {
                for (Instance instance : reservation.instances()) {
                    String name=instance.tags().get(0).value();
                    String state=instance.state().name().toString();
                    if(name.equals("Manager")&&(state.equals("running")||state.equals("pending"))) {
                        ec2.terminate(instance.instanceId());
                    }
                }
            }
        } catch (Ec2Exception e) {
            System.out.println("Problem in function: TerminateManager");
            System.out.println(e.awsErrorDetails().errorMessage());
        }
    }
///////////////////////////////////////////////////////////////////////////////////////////////////////////


    public void run() {
        try {
            ManagerAndAppQ = new SQS("ManagerAndAppQ");
            ManagerAndAppQ.requestQueueURL();
            workersThread tw= new workersThread();
            WorkerActionThread = new Thread(tw);
            while(!terminate){
                CheckingMsgFromApp();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}