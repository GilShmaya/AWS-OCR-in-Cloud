import services.S3;
import services.SQS;

public class Manager {

    public static void main(String[] args) throws InterruptedException {

        S3 s3 = new S3();

        SQS ManagerToWorkers = new SQS("managerToWorkersQ" );
        ManagerToWorkers.create();
        AppManagerContact ContactWithApp =new AppManagerContact(s3,ManagerToWorkers);

        SQS WorkersToManager = new SQS("managerToWorkersQ");
        WorkersToManager.create();
        ManagerWorkersContact ContactWithWorkers= new ManagerWorkersContact(WorkersToManager);


        Thread mainAppThread = new Thread(ContactWithApp);
        mainAppThread.start();
        Thread workersThread = new Thread(ContactWithWorkers);
        workersThread.start();

        try{
            mainAppThread.join();
        }
        catch(InterruptedException e){
            e.printStackTrace();
        }
        try{
            workersThread.join();
        }
        catch(InterruptedException e){
            e.printStackTrace();
        }
    }
}
