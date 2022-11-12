package services;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.*;
import java.util.LinkedList;

public class DataBase {
    private static final String WORKER_DATA = ""; // TODO!
    private S3 s3;
    private LinkedList<EC2> workersList;
    private int workersAmount;
    private int tasksAmount;


    private static class singletonHolder {
        private static DataBase instance = new DataBase();
    }

    public static DataBase getInstance() {
        return singletonHolder.instance;
    }

    private DataBase() {
        s3 = new S3();
        workersList = new LinkedList<EC2>();
        workersAmount = 0;
        tasksAmount = 0;
    }

    public synchronized boolean isAvailableWorker() {
        return !workersList.isEmpty();
    }

    public synchronized int getWorkersAmount() {
        return workersAmount;
    }

    public LinkedList<EC2> getWorkersList() {
        return workersList;
    }

    public synchronized void addWorker(EC2 worker) {
        workersAmount++;
        workersList.add(worker);
    }

    public synchronized void addAmountOfWorkers(int amountOfWorkersNeeded){
        while (amountOfWorkersNeeded > 0) {
            EC2 worker = new EC2("worker" + workersAmount, 1, 1, WORKER_DATA);
            addWorker(worker);
            amountOfWorkersNeeded--;
        }
    }

    public synchronized EC2 assignWorker() {
        if (!isAvailableWorker()) {
            return null;
        } else {
            workersAmount--;
            return workersList.removeFirst();
        }
    }

    public void removeWorker(EC2 worker) {
        workersList.remove(worker);
        workersAmount--;
    }

    public int getTasksAmount() {
        return tasksAmount;
    }

    public void addTask() {
        tasksAmount++;
    }

    public void removeTask(String key, String bucketName) {
        s3.deleteObject(key, bucketName);
        tasksAmount--;
    }

    private File getObjectS3(String path, String fileName, String bucket){ // TODO!
        ResponseBytes<GetObjectResponse> responseBytes = s3.getObjectBytes(fileName, bucket);
        byte[] objectData = responseBytes.asByteArray();

        File file = new File(path);
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            outputStream.write(objectData);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //s3.DeleteObject(fileName, bucket);
        return file;
    }

    public boolean SetImgResult(String res, String bucket) throws IOException { // TODO!
        String path = System.getProperty("user.dir") + "/SummaryFile.txt";
        boolean flag= true;
        File summaryFilein= null;
        try{
            summaryFilein= getObjectS3(path, "SummaryFile.txt",bucket);
        }
        catch (Exception e){
            e.printStackTrace();
            flag= false;
        }
        if (flag) {
            BufferedReader reader = new BufferedReader(new FileReader(summaryFilein));
            String line = null;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if ((line.length() >= 11) && (line.equals("nothing yet"))) {
                summaryFilein.delete();
                String path1 = System.getProperty("user.dir") + "/SummaryFile.txt";
                File summaryFilein2 = new File(path1);
                try {
                    FileWriter fw = new FileWriter(summaryFilein2.getName(), true); //the true will append the result
                    fw.write(res);
                    fw.close();
                } catch (IOException ioe) {
                    System.err.println("IOException: " + ioe.getMessage());
                }
                s3.putObject(path1, bucket, "SummaryFile.txt");
                summaryFilein2.delete();
            } else {
                try {
                    FileWriter fw = new FileWriter(summaryFilein.getName(), true); //the true will append the result
                    fw.write(res);
                    fw.close();
                } catch (IOException ioe) {
                    System.err.println("IOException: " + ioe.getMessage());
                }
                s3.putObject(path, bucket, "SummaryFile.txt");
                summaryFilein.delete();
            }

            return true;
        }
        else {
            return false;
        }
    }

    public boolean SetTaskImg(String res, String bucket) throws IOException { // TODO!
        //write res to the summaryFile
        System.out.println("*** before writing the result to summary file ***\n");
        boolean isOk = SetImgResult(res, bucket);
        if (isOk) {
            System.out.println("**** after writing the rusult to summary file *** \n");
            String path = System.getProperty("user.dir") + "/OCRFile.txt";

            System.out.println("*** getting the OCRFile from S3 ***\n");
            File OCRFilein = getObjectS3(path, "OCRFile.txt", bucket);

            System.out.println("*** reading the OCRFile from the computer***\n");
            //check how many urls there are left 0-return true else- false
            BufferedReader reader = new BufferedReader(new FileReader(OCRFilein));
            String line = null;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            OCRFilein.delete();
            Integer count = Integer.parseInt(line);
            count--;
            System.out.println("*** the number of images left is: " + count + " ***\n");
            if (count == 0) {
                return true;
            } else {
                //put a new file to s3, with the new number
                System.out.println("*** writing the number of images left to a file ***\n");
                String OCRFilePath = System.getProperty("user.dir") + "/OCRFile.txt";
                File OCRFile = new File(OCRFilePath);
                OutputStream outputStreamOCR = new FileOutputStream(OCRFile);
                byte[] dataC = count.toString().getBytes();
                outputStreamOCR.write(dataC);
                outputStreamOCR.flush();
                outputStreamOCR.close();
                System.out.println("*** uploading OCRfile back to S3 ***\n");
                s3.putObject(OCRFilePath, bucket, "OCRFile.txt");
                OCRFile.delete();
                return false;
            }
        }
        return true;
    }
}
