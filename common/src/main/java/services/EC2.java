package services;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.Base64;

public class EC2 {
    private String AMI_ID = ""; // TODO
    private Ec2Client ec2Client;
    private String ec2Name;
    private String instanceId;

    public EC2(String ec2Name, int minCount, int maxCount, String userData) {
        this.ec2Name = ec2Name;
        ec2Client = Ec2Client.builder().build();

        IamInstanceProfileSpecification IAM_role = IamInstanceProfileSpecification.builder()
                .arn("arn:aws:iam::263387240235:instance-profile/IAM_Ass1_1").build(); // TODO: change

        RunInstancesRequest request = RunInstancesRequest.builder()
                .imageId(AMI_ID)
                .instanceType(InstanceType.T2_MICRO)
                .securityGroupIds("sg-047c03574a9f9bc2f") // TODO: change
                .iamInstanceProfile(IAM_role)
                .maxCount(maxCount)
                .minCount(minCount)
                .userData(Base64.getEncoder().encodeToString(userData.getBytes()))
                .build();

        RunInstancesResponse response = ec2Client.runInstances(request);
        instanceId = response.instances().get(0).instanceId();
        Tag tag = Tag.builder()
                .key("Name")
                .value(ec2Name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();
        try {
            ec2Client.createTags(tagRequest);
            System.out.printf("Successfully started EC2 Instance %s based on AMI %s", instanceId, AMI_ID);
        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }

    public Ec2Client getEC2Client() {
        return ec2Client;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void terminate() {
        try {
            TerminateInstancesRequest TerminateRequest = TerminateInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();
            ec2Client.terminateInstances(TerminateRequest);
            System.out.printf("Successfully terminated EC2 Instance %s based on AMI %s", instanceId, AMI_ID);
        } catch (Ec2Exception e) {
            System.out.println(e.awsErrorDetails().errorMessage());
        }
    }

    public void terminate(String instanceId){
        try {
            TerminateInstancesRequest TerminateRequest = TerminateInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();
            ec2Client.terminateInstances(TerminateRequest);
            System.out.printf("Successfully terminated EC2 Instance %s based on AMI %s", instanceId, AMI_ID);
        }     catch (Ec2Exception e){
            System.out.println(e.awsErrorDetails().errorMessage());
        }
    }
}
