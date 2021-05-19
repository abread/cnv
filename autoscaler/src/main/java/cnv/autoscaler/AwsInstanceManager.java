package cnv.autoscaler;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.UpdateSecurityGroupRuleDescriptionsIngressRequest;

import java.util.Arrays;

public class AwsInstanceManager {
    private static AmazonEC2 ec2;
    private final static String AVAILABILITY_ZONE = "eu-west-2";
    private final static String INSTANCE_IMAGE_ID = "ami-0fbec3e0504ee1970";
    private final static String INSTANCE_TYPE = "t2.micro";
    private final static String INSTANCE_SECURITY_GROUP = "ssh+http8000";
    private final static String INSTANCE_KEYPAIR_NAME = "cnv-aws";

    static {
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new RuntimeException("Cannot load EC2 credentials. Make sure the file is at the correct location (~/.aws/credentials) and is valid", e);
        }
        ec2 = AmazonEC2ClientBuilder.standard()
                            .withRegion(AVAILABILITY_ZONE)
                            .withCredentials(new AWSStaticCredentialsProvider(credentials))
                            .build();

        setupInstanceSecurityGroup();
    }

    public static Instance launchInstance() {
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId(INSTANCE_IMAGE_ID)
            .withInstanceType(INSTANCE_TYPE)
            .withMinCount(1)
            .withMaxCount(1)
            .withKeyName(INSTANCE_KEYPAIR_NAME)
            .withSecurityGroups(INSTANCE_SECURITY_GROUP);
        RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);

        return runInstancesResult.getReservation().getInstances().get(0);
    }

    public static void terminateInstance(String id) {
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(id);
        ec2.terminateInstances(termInstanceReq);
    }

    public static void setupInstanceSecurityGroup() {
        CreateSecurityGroupRequest createReq = new CreateSecurityGroupRequest(INSTANCE_SECURITY_GROUP, "CNV radarscanner instances security group");
        ec2.createSecurityGroup(createReq);

        UpdateSecurityGroupRuleDescriptionsIngressRequest updateReq = new UpdateSecurityGroupRuleDescriptionsIngressRequest();
        updateReq.setGroupName(INSTANCE_SECURITY_GROUP);
        updateReq.setIpPermissions(Arrays.asList(
            new IpPermission().withToPort(8000).withIpProtocol("tcp"),
            new IpPermission().withToPort(22).withIpProtocol("tcp")
        ));
        ec2.updateSecurityGroupRuleDescriptionsIngress(updateReq);
    }
}
