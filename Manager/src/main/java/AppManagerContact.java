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
            e.printStackTrace();
        }
        System.out.println("There are: "+counter+ "URLs in the file");
        return counter;
    }

    public File fileFromS3 (String name, String key, String bucket) throws IOException {

        ResponseBytes<GetObjectResponse> s3File= s3.getObjectBytes(key,bucket);
        byte [] data= s3File.asByteArray();
        String path= System.getProperty("user.dir")+"/"+name+".txt";
        File Input= new File(path);
        OutputStream outputStream= new FileOutputStream(Input);
        outputStream.write(data);
        outputStream.flush();
        outputStream.close();
        return Input;
    }


    public void resultFile (String name, String bucket) throws IOException {
        String ansFilePath= System.getProperty("user.dir")+"/SummaryFile.txt";
        File ansFile= new File (ansFilePath);
        OutputStream outputStream= new FileOutputStream(ansFile);
        byte [] data= "nothing yet".getBytes();
        outputStream.write(data);
        outputStream.flush();
        outputStream.close();
        s3.putObject(ansFilePath, bucket, "SummaryFile.txt");
        System.out.println("--- Add Task: "+name+" ---");
        ansFile.delete(); //delete the file after we finished with it (scalability)
    }


    public void OCRfile(File input, String bucket) throws IOException {

        // first write in a new file the number of urls
        Integer NumOfURL= numOfURLs(input);
        String PathOCR= System.getProperty("user.dir")+"/OCRFile.txt";
        File OCR= new File (PathOCR);
        OutputStream outputStreamOCR= new FileOutputStream(OCR);
        System.out.println(" --- Image count :" + NumOfURL.toString()+" ---");
        byte [] urls= NumOfURL.toString().getBytes();
        outputStreamOCR.write(urls);
        outputStreamOCR.flush();
        outputStreamOCR.close();
        System.out.println(" --- put the OCR file with the number of URLs in S3 ---\n");
        //set the file of the url number to the bucket of the local application
        s3.putObject(PathOCR, bucket, "OCRFile.txt");
        OCR.delete();
    }


    // Creates an SQS message for each URL in the input file together with the operation
    //that should be performed on it
    public int splitAndSendTask (File InputFile, String Task_key, String bucket, String LocalQueue) throws FileNotFoundException {
        int counter=0;
        try{
            BufferedReader reader = new BufferedReader(new FileReader(InputFile));
            String line = reader.readLine();
            while (line != null) { // there are more tasks to process
                String [] Line= line.split("\n");
                String msg = Task_key+"_"+counter+" "+Line[0]+" "+bucket+ " "+ LocalQueue;
                ManagerAndWorkersQ.send(msg);
                line = reader.readLine();
                counter++;
            }
            reader.close();
        }
        catch (IOException exception){
            System.out.println("Something is wrong with function: splitAndSendTask");
            exception.printStackTrace();
        }
        return counter;
    }


    // delete the task from s3 after reading and analyzed it
    // delete the inputFile we got from s3
    // delete the message from ManagerAndAppQ after sending it to the workers
    public void finishWithTask (String key, String name, String bucket, Message msg, File input) {
        System.out.println("--- Deleting task: "+ name+ " Key: "+key+ " from bucket: "+ bucket +" ---");
        s3.deleteObject(key,bucket);
        input.delete();
        DB.addTask();
        List<Message> delete= new LinkedList<Message>();
        delete.add(msg);
        ManagerAndAppQ.deleteMessages(delete);
    }


    public void newTaskFromApp (Message msg) throws IOException {
        if(!terminate) {
            System.out.println("---Processing New Task---\n");

            // starting with reading the message
            String str= msg.body();
            String[] split = str.split(" ");
            if (split.length != 5) {
                System.out.println("missing necessary data");
                System.exit(1);
            }
            String TaskName = "Task" + new Date().getTime();
            String key = split[3];
            int n = Integer.parseInt(split[2]);
            String bucket= split[4];
            String LocalQueue= split[1];

            File ourFile = fileFromS3(TaskName, key, bucket); //Downloads the input file from S3
            OCRfile(ourFile, bucket); // creating an OCR file with the number of urls in the input message & sending it to s3
            resultFile(TaskName, bucket); // creating an empty file for the worker's results


            // Checks how many workers are necessary for the new task and update it in dataBase
            int numOfWorkers = workersForTask(numOfURLs(ourFile),n);
            DB.addAmountOfWorkers(numOfWorkers);

            // starting the thread only if it's the beginning of the process
            if (count==0){
                count++;
                WorkerActionThread.start();
            }

            // Starts Worker processes (nodes) according to the necessary amount
            System.out.println("--- Now send the tasks to the workers --- \n");
            Integer splittingTasks = splitAndSendTask(ourFile, TaskName, bucket, LocalQueue);

            finishWithTask(key, TaskName, bucket, msg, ourFile);
        }
    }


    // The Manager checks a special SQS queue for messages from local applications.
    public void CheckingMsgFromApp() throws InterruptedException, IOException {
        List<Message> messages= ManagerAndAppQ.getMessages();
        if (!messages.isEmpty()){
            System.out.println(" --- A new message from local application is waiting ---");
            // Once it receives a message he reads it and checks if it's a termination msg or a new task msg
            for (Message msg : messages){
                System.out.println("--- The new message is: "+msg.body()+" ---\n");
                if(msg.body().startsWith("Terminate")) {
                    Terminate();
                    return;
                }
                else if(msg.body().startsWith("Task")){
                    newTaskFromApp(msg);
                }
            }
        }
    }


    public void terminateWorkers () {
        List<EC2> workers = DB.getWorkersList();
        for (EC2 worker : workers) {
            worker.terminate();
        }
    }

    public void Terminate() throws InterruptedException {

        System.out.println("--- Waiting for all activates workers to finish and then terminate --- ");
        terminate=true; // Does not accept any more input files from local applications.
        while(DB.getTasksAmount()>0){ // Waits for all the workers to finish their job, and then terminates them.
            try {
                TimeUnit.SECONDS.sleep(45);
            } catch (InterruptedException exception) {
            }
        }
        DB.terminate();
        System.out.println("--- All workers finished their job ---");
        WorkerActionThread.interrupt();
        terminateWorkers();

        // Terminate the local application to manager SQS
        System.out.println("delete the queues");
        ManagerAndWorkersQ.remove();
        ManagerAndAppQ.remove();

        EC2 ec2= new EC2();
        try {
            DescribeInstancesRequest req = DescribeInstancesRequest.builder().build();
            DescribeInstancesResponse res = ec2.getEC2Client().describeInstances(req);
            for (Reservation reservation : res.reservations()) {
                for (Instance instance : reservation.instances()) {
                    String name=instance.tags().get(0).value();
                    String state=instance.state().name().toString();
                    if(name.equals("manager")&&(state.equals("running")||state.equals("pending"))) {
                        ec2.terminate(instance.instanceId());
                    }
                }
            }
        } catch (Ec2Exception e) {
            System.out.println("Problem in function: TerminateManager");
            System.out.println(e.awsErrorDetails().errorMessage());
        }
    }


    public void run() {
        try {
            ManagerAndAppQ = new SQS("localToManagerSQS");
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