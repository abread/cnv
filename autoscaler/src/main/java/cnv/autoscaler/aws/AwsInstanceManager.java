package cnv.autoscaler.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.IpRange;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.AddRoleToInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreateInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.CreatePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;

import cnv.autoscaler.Main;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Wrapper class around AWS EC2 and IAM APIs.
 */
public class AwsInstanceManager {
    private static final AmazonIdentityManagement iam;
    private static final AmazonEC2 ec2;
    private static final AmazonCloudWatch cloudWatch;

    private final static String REGION = "eu-west-2";
    private final static String INSTANCE_IMAGE_NAME = "ami-radarscanner";
    private final static String INSTANCE_TYPE = "t2.micro";
    private final static String INSTANCE_SECURITY_GROUP = "ssh+http8000";
    private final static String INSTANCE_KEYPAIR_NAME = "cnv-aws";
    private final static String INSTANCE_IAM_ROLE_NAME = "radarscanner-instance";
    private final static String INSTANCE_IAM_PROFILE_NAME = INSTANCE_IAM_ROLE_NAME;
    private final static String INSTANCE_IAM_ROLE_POLICY_DOC = loadResource("instance_iam_role_policy.json");
    private final static String INSTANCE_IAM_POLICY_NAME = "radarscanner-instance-policy";
    private final static String INSTANCE_IAM_POLICY_DOC = loadResource("instance_iam_policy.json");

    private final static String INSTANCE_IMAGE_ID;

    static {
        AWSCredentialsProvider credentialsProvider = AwsCredentials.credentialsProvider();

        ec2 = AmazonEC2ClientBuilder.standard().withRegion(REGION).withCredentials(credentialsProvider).build();

        cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion(REGION).withCredentials(credentialsProvider)
                .build();

        iam = AmazonIdentityManagementClientBuilder.standard().withRegion(REGION).withCredentials(credentialsProvider)
                .build();

        DescribeImagesResult images = ec2.describeImages(new DescribeImagesRequest()
                .withFilters(new Filter().withName("name").withValues(INSTANCE_IMAGE_NAME)).withOwners("self"));
        assert images.getImages().size() == 1;
        INSTANCE_IMAGE_ID = images.getImages().get(0).getImageId();

        setupIamInstanceProfile();
        setupInstanceSecurityGroup();
    }

    /**
     * Loads a resource file
     * @param name the name of the resource file
     * @return the file contents as a String
     */
    private static String loadResource(String name) {
        try {
            InputStream is = Main.class.getClassLoader().getResourceAsStream(name);
            return new String(readAllBytes(is));
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to load resource \"%s\"", name), e);
        }
    }

    /**
     * Reads all bytes from an InputStream and returns them in a byte[]
     * @param is the input stream to be read
     * @return the corresponding byte[]
     * @throws IOException if the InputStream can't be read or closed
     */
    private static byte[] readAllBytes(InputStream is) throws IOException {
        final int BUF_SIZE = 4096;
        byte[] buffer = new byte[BUF_SIZE];

        int offset = 0;
        int nRead;
        while ((nRead = is.read(buffer, offset, buffer.length - offset)) != -1) {
            offset += nRead;

            if (buffer.length == offset) {
                byte[] newBuffer = new byte[buffer.length + BUF_SIZE];
                System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                buffer = newBuffer;
            }
        }

        is.close();

        if (buffer.length != offset) {
            byte[] newBuffer = new byte[offset];
            System.arraycopy(buffer, 0, newBuffer, 0, offset);
            buffer = newBuffer;
        }

        return buffer;
    }

    /**
     * Requestes EC2 to launch n instances with advanced monitoring. The instances are described
     * by the static variables such as INSTANCE_IMAGE_ID and INSTANCE_TYPE. This function waits for the requested
     * instances to start.
     * @param n the number of instances to launch
     * @return a list of Instance objects describing the launched instances
     */
    public static List<Instance> launchInstances(int n) {
        final long WAIT_TIME = 1 * 1000; // ms
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId(INSTANCE_IMAGE_ID).withInstanceType(INSTANCE_TYPE).withMinCount(n)
                .withMaxCount(n).withKeyName(INSTANCE_KEYPAIR_NAME).withSecurityGroups(INSTANCE_SECURITY_GROUP)
                .withIamInstanceProfile(new IamInstanceProfileSpecification().withName(INSTANCE_IAM_PROFILE_NAME))
                .withMonitoring(true);
        RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);

        String reservationId = runInstancesResult.getReservation().getReservationId();
        List<Instance> instances = runInstancesResult.getReservation().getInstances();
        while (instances.stream().map(Instance::getState).anyMatch(state -> state.getName().equals("pending"))) {
            // wait for all instances to start and make sure we have fresh metadata for them
            instances = ec2
                    .describeInstances(new DescribeInstancesRequest()
                            .withFilters(new Filter().withName("reservation-id").withValues(reservationId)))
                    .getReservations().get(0).getInstances();

            try {
                Thread.sleep(WAIT_TIME);
            } catch (InterruptedException ignored) {
                // we wanted to let you work but ok
            }
        }

        return instances;
    }

    /**
     * Returns the public DNS name for a given instanceId.
     * @param instanceId the AWS id of the instance
     * @return the corresponding DNS name
     */
    public static String getPublicDnsName(String instanceId) {
        return ec2.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId)).getReservations()
                .get(0).getInstances().get(0).getPublicDnsName();
    }

    /**
     * Returns the average CPU Usage for a given instance id. The CPU Usage is fetched using the CloudWatch API
     * for a period of the last 60 seconds. If no sufficient data could be found regarding that instance, this method
     * returns an empty Optional.
     * @param instanceId the AWS id of the instance
     * @return the corresponding CPU usage if available, empty if not
     */
    public static Optional<Double> getAvgCpuUsage(String instanceId) {
        final long ONE_MIN_IN_MILLIS = 1000 * 60 * 10;
        final Date endTime = new Date();
        final Date startTime = new Date(endTime.getTime() - ONE_MIN_IN_MILLIS);

        final List<Dimension> dims = new ArrayList<Dimension>();
        dims.add(new Dimension().withName("InstanceId").withValue(instanceId));

        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest().withStartTime(startTime)
                .withEndTime(endTime).withPeriod(60 /* s */).withNamespace("AWS/EC2").withDimensions(dims)
                .withMetricName("CPUUtilization").withStatistics("Average");

        GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);

        return getMetricStatisticsResult.getDatapoints().stream()
                .map(dp -> dp.getAverage() / 100 /* use 0-1 percentages */).findFirst();
    }

    /**
     * @param ids the ids of the instances to terminate
     */
    public static void terminateInstances(String... ids) {
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(ids);
        ec2.terminateInstances(termInstanceReq);
    }

    /**
     * Creates an AWS security group to be used with the corresponding permissions and open ports.
     */
    public static void setupInstanceSecurityGroup() {
        CreateSecurityGroupRequest createReq = new CreateSecurityGroupRequest(INSTANCE_SECURITY_GROUP,
                "CNV radarscanner instances security group");

        try {
            ec2.createSecurityGroup(createReq);
        } catch (AmazonEC2Exception ignored) {
            // it already exists, probably (if not the next request will fail anyway)
        }

        IpRange allIpv4 = new IpRange().withCidrIp("0.0.0.0/0");

        IpPermission tcp8000 = new IpPermission().withIpProtocol("tcp").withToPort(8000).withFromPort(8000)
                .withIpv4Ranges(allIpv4);

        IpPermission tcp22 = new IpPermission().withIpProtocol("tcp").withToPort(22).withFromPort(22)
                .withIpv4Ranges(allIpv4);

        try {
            ec2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest()
                    .withGroupName(INSTANCE_SECURITY_GROUP).withIpPermissions(tcp8000, tcp22));
        } catch (AmazonEC2Exception e) {
            if (!e.getErrorCode().equals("InvalidPermission.Duplicate")) {
                throw e;
            }
        }
    }

    /**
     * Sets up an IAM instance profile. The instance profile takes time to be available and waiting/polling is
     * required.
     */
    public static void setupIamInstanceProfile() {
        final int MAX_TRIES = 5;
        int curr_tries = 0;
        try {
            iam.createRole(new CreateRoleRequest().withRoleName(INSTANCE_IAM_ROLE_NAME)
                    .withAssumeRolePolicyDocument(INSTANCE_IAM_ROLE_POLICY_DOC));

            iam.createInstanceProfile(
                    new CreateInstanceProfileRequest().withInstanceProfileName(INSTANCE_IAM_PROFILE_NAME));
            iam.addRoleToInstanceProfile(new AddRoleToInstanceProfileRequest()
                    .withInstanceProfileName(INSTANCE_IAM_PROFILE_NAME).withRoleName(INSTANCE_IAM_ROLE_NAME));

            String policyArn = iam.createPolicy(new CreatePolicyRequest().withPolicyName(INSTANCE_IAM_POLICY_NAME)
                    .withPolicyDocument(INSTANCE_IAM_POLICY_DOC)).getPolicy().getArn();
            iam.attachRolePolicy(
                    new AttachRolePolicyRequest().withRoleName(INSTANCE_IAM_ROLE_NAME).withPolicyArn(policyArn));

            // I hate the AWS API
            while (iam.listInstanceProfiles().getInstanceProfiles().stream()
                    .noneMatch(instProfile -> instProfile.getInstanceProfileName().equals(INSTANCE_IAM_PROFILE_NAME))) {
                curr_tries++;
                if (curr_tries >= MAX_TRIES) {
                    System.err.println("Could not find the Instance Profile in AWS IAM");
                    System.exit(1);
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
            }
        } catch (Exception ignored) {
        }
    }
}
