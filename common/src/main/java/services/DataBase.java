package services;

import java.io.*;
import java.util.LinkedList;

public class DataBase {
    private static final String WORKER_DATA = ""; // TODO!
    private String USER_DIR_PROPERTY = System.getProperty("user.dir");
    private String SUMMARY_FILE_PATH = USER_DIR_PROPERTY + "/SummaryFile.txt";
    private String OCR_FILE_PATH = USER_DIR_PROPERTY + "/OCRFile.txt";
    private S3 s3;
    private LinkedList<EC2> workersList;
    private int workersAmount;
    private int tasksAmount;
    private boolean terminate = false;


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

    public synchronized void addAmountOfWorkers(int amountOfWorkersNeeded) {
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

    public boolean handleOneResult(String summaryFileLineContext, String bucketName) throws IOException {
        // write the OCR result (url + images' text)  to the summary file
        if (writeLineToS3Object(summaryFileLineContext, bucketName)) { // writing succeed
            // get the OCRFile from S3 after adding the new line
            File OCRFile = writeS3ObjectToFile(OCR_FILE_PATH, "OCRFile.txt", bucketName);
            // update OCR file
            BufferedReader readerOCRFile = new BufferedReader(new FileReader(OCRFile));
            String line = null;
            try {
                line = readerOCRFile.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            assert line != null;
            int count = Integer.parseInt(line);
            count--;
            System.out.println("There are " + count + " number of images left");
            OCRFile.delete();
            if (count == 0) {
                return true;
            } else { // upload a new OCR file in s3 that include the number of the images left
                File OCRFileNew = new File(OCR_FILE_PATH);
                OutputStream outputStreamOCR = new FileOutputStream(OCRFileNew);
                byte[] countBytes = Integer.toString(count).getBytes();
                outputStreamOCR.write(countBytes);
                outputStreamOCR.flush();
                outputStreamOCR.close();
                s3.putObject(OCR_FILE_PATH, bucketName, "OCRFile.txt");
                OCRFileNew.delete();
                return false;
            }
        }
        return true;
    }

    public boolean writeLineToS3Object(String summaryFileLineContext,
                                       String bucketName) {
        try {
            File summaryFile = writeS3ObjectToFile(SUMMARY_FILE_PATH, "SummaryFile.txt", bucketName);
            BufferedReader reader = new BufferedReader(new FileReader(summaryFile));
            String line = reader.readLine();
            if (line.startsWith("nothing yet")) { // no result was written yet, this is the first result.
                summaryFile.delete();
            }
            FileWriter fileWriter = new FileWriter(summaryFile.getName(), true);
            fileWriter.write(summaryFileLineContext);
            fileWriter.close();
            s3.putObject(SUMMARY_FILE_PATH, bucketName, "SummaryFile.txt");
            summaryFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private File writeS3ObjectToFile(String path, String keyFileName, String bucketName) {
        byte[] objectData = s3.getObjectBytes(keyFileName, bucketName).asByteArray();

        File file = new File(path);
        try {
            OutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(objectData);
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    // switch the state - got terminate.
    public synchronized void terminate(){
        terminate = true;
    }

    public synchronized boolean isTerminate(){
        return terminate;
    }
}