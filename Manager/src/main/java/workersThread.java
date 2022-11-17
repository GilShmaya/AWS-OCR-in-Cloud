import services.DataBase;
import services.EC2;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class workersThread implements Runnable{
    public void run() {
        EC2 ec2= new EC2();
        DataBase DB= DataBase.getInstance();
        while(true) {
            try {
                TimeUnit.SECONDS.sleep(45);
            } catch (InterruptedException exception) {
                exception.printStackTrace();
            }
            LinkedList<EC2> workers = DB.getWorkersList();
            // System.out.println("*** workers list ****"); //todo: necessary?
            if (!workers.isEmpty()) {
                for (EC2 oneWorker : workers) {
                    // System.out.println("**********Start**************"); //todo: necessary?
                    DescribeInstancesRequest request = DescribeInstancesRequest.builder().instanceIds(oneWorker.getInstanceId()).build();
                    DescribeInstancesResponse result = ec2.getEC2Client().describeInstances(request);
                    // System.out.println("*** finish res ***"); //todo: necessary?
                    for (Reservation reservation : result.reservations()) {
                        for (Instance instance : reservation.instances()) {
                            if(instance.instanceId().equals(oneWorker.getInstanceId())) {
                                //System.out.println("*** after fors ***"); //todo: necessary?
                                String state = instance.state().name().toString();
                                // System.out.println("*** got the state ***"); //todo: necessary?
                                if (state.equals("shutting-down") || state.equals("terminated") || state.equals("stopping") || state.equals("stopped")) {
                                    System.out.println("**********Found**************");
                                    DB.removeWorker(oneWorker); // todo : check method's name
                                    DB.addAmountOfWorkers(1); //todo
                                    try {
                                        TimeUnit.SECONDS.sleep(45);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    System.out.println("**********ADD**************");
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}