package services;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.Base64;

public class EC2 {
    private static final software.amazon.awssdk.regions.Region REGION = Region.US_EAST_1;
    private String AMI_ID = "ami-07b0cb5b5abb9dff7";
    private Ec2Client ec2Client;
    private String ec2Name;
    private String instanceId;

    public EC2() {
        ec2Client = Ec2Client.builder().region(REGION).build();
    }


    public EC2(String ec2Name, int minCount, int maxCount, String userData) {
        this.ec2Name = ec2Name;
        ec2Client = Ec2Client.builder().region(REGION).build();

        IamInstanceProfileSpecification IAM_role = IamInstanceProfileSpecification.builder()
                .arn("arn:aws:iam::262099114720:instance-profile/LabInstanceProfile").build();

        RunInstancesRequest request = RunInstancesRequest.builder()
                .imageId(AMI_ID)
                .instanceType(InstanceType.T2_MICRO)
                .securityGroupIds("sg-0d564df68a2822f15")
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
            System.out.printf("Successfully started EC2 Instance with name %s, ID %s based on AMI %s\n",
                    ec2Name,
                    instanceId,
                    AMI_ID);
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
            System.out.println("Successfully terminated EC2 Instance %s based on AMI " + instanceId + AMI_ID);
        } catch (Ec2Exception e) {
            System.out.println(e.awsErrorDetails().errorMessage());
        }
    }

    public void terminate(String instanceId) {
        try {
            TerminateInstancesRequest TerminateRequest = TerminateInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();
            ec2Client.terminateInstances(TerminateRequest);
            System.out.println("Successfully terminated EC2 Instance %s based on AMI " + instanceId + AMI_ID);
        } catch (Ec2Exception e) {
            System.out.println(e.awsErrorDetails().errorMessage());
        }
    }
}
