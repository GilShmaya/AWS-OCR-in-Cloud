import services.S3;
import services.SQS;

public class Manager {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- Create a new s3 ---");
        S3 s3 = new S3();
        System.out.println("--- Create a new SQS for the manager messages to the workers ---");
        SQS ManagerToWorkers = new SQS("managerToWorkersQ" );
        ManagerToWorkers.create();
        AppManagerContact ContactWithApp =new AppManagerContact(s3,ManagerToWorkers);
        System.out.println("--- Create a new SQS for the workers messages to the manager ---");
        SQS WorkersToManager = new SQS("workersToManagerQ");
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
