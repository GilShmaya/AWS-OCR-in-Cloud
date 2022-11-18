import services.S3;
import services.SQS;

public class Manager {

    private static Thread MainAppThread;
    private static Thread WorkersThread;

    public static void main(String[] args) throws InterruptedException {

        S3 s3 = new S3();

        SQS ManagerToWorkers = new SQS("managerToWorkersQ" );
        ManagerToWorkers.create();
        AppManagerContact ContactWithApp =new AppManagerContact(s3,ManagerToWorkers);

        SQS WorkersToManager = new SQS("managerToWorkersQ");
        WorkersToManager.create();
        ManagerWorkersContact ContactWithWorkers= new ManagerWorkersContact(WorkersToManager);


        MainAppThread= new Thread(ContactWithApp);
        MainAppThread.start();
        WorkersThread= new Thread(ContactWithWorkers);
        WorkersThread.start();

        try{
            MainAppThread.join();
        }
        catch(InterruptedException e){
            e.printStackTrace();
        }
        try{
            WorkersThread.join();
        }
        catch(InterruptedException e){
            e.printStackTrace();
        }
    }
}
