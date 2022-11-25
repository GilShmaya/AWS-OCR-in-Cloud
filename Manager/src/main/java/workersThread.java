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
        DataBase dataBase= DataBase.getInstance();
        while(!dataBase.isTerminate()) {
            try {
                TimeUnit.SECONDS.sleep(45);
            } catch (InterruptedException exception) {
            }
            LinkedList<EC2> workers = dataBase.getWorkersList();
            if (!workers.isEmpty()) {
                for (EC2 worker : workers) {
                    DescribeInstancesRequest ask = DescribeInstancesRequest.builder().instanceIds(worker.getInstanceId()).build();
                    DescribeInstancesResponse ans = ec2.getEC2Client().describeInstances(ask);
                    for (Reservation reservation : ans.reservations()) {
                        for (Instance instance : reservation.instances()) {
                            if(instance.instanceId().equals(worker.getInstanceId())) {
                                String state = instance.state().name().toString();
                                if (state.equals("shutting-down") || state.equals("terminated") || state.equals("stopping") || state.equals("stopped")) {
                                    dataBase.removeWorker(worker);
                                    dataBase.addAmountOfWorkers(1);
                                    try {
                                        TimeUnit.SECONDS.sleep(45);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}