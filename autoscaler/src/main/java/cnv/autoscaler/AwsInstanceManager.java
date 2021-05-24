package cnv.autoscaler;

import com.amazonaws.AmazonServiceException.ErrorType;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.internal.CredentialsEndpointProvider;
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
import com.amazonaws.services.ec2.model.DescribeInstanceAttributeRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.IpRange;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.UpdateSecurityGroupRuleDescriptionsIngressRequest;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.AddRoleToInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreateInstanceProfileRequest;
import com.amazonaws.services.identitymanagement.model.CreatePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class AwsInstanceManager {
    private Logger logger = Logger.getLogger(AwsInstanceManager.class.getName());

    private static AmazonIdentityManagement iam;
    private static AmazonEC2 ec2;
    private static AmazonCloudWatch cloudWatch;

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
        AWSCredentialsProvider credentialsProvider;
        try {
            AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
            credentialsProvider = new AWSStaticCredentialsProvider(credentials);
        } catch (Exception ignored) {
            credentialsProvider = InstanceProfileCredentialsProvider.getInstance();
        }

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

    private static String loadResource(String name) {
        try {
            URI uri = Main.class.getClassLoader().getResource(name).toURI();
            byte[] bytes = Files.readAllBytes(Paths.get(uri));
            return new String(bytes);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(String.format("Failed to load resource \"%s\"", name), e);
        }
    }

    public static List<Instance> launchInstances(int n) {
        final long WAIT_TIME = 2 * 1000; // ms
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

    public static String getPublicDnsName(String instanceId) {
        return ec2.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId)).getReservations()
                .get(0).getInstances().get(0).getPublicDnsName();
    }

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

        return getMetricStatisticsResult.getDatapoints().stream().map(dp -> dp.getAverage()).findFirst();
    }

    public static void terminateInstances(String... ids) {
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(ids);
        ec2.terminateInstances(termInstanceReq);
    }

    public static void setupInstanceSecurityGroup() {
        CreateSecurityGroupRequest createReq = new CreateSecurityGroupRequest(INSTANCE_SECURITY_GROUP,
                "CNV radarscanner instances security group");

        try {
            ec2.createSecurityGroup(createReq);
        } catch (AmazonEC2Exception ignored) {
            // it already exists, probably (if not the next request will fail anyway)
        }

        // FIXME: The permissions were not being applied, test if this is now working
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

    public static void setupIamInstanceProfile() {
        try {
        String policyArn = iam.createPolicy(new CreatePolicyRequest().withPolicyName(INSTANCE_IAM_POLICY_NAME).withPolicyDocument(INSTANCE_IAM_POLICY_DOC)).getPolicy().getArn();
        iam.createRole(new CreateRoleRequest().withRoleName(INSTANCE_IAM_ROLE_NAME)
                .withAssumeRolePolicyDocument(INSTANCE_IAM_ROLE_POLICY_DOC));

        iam.attachRolePolicy(new AttachRolePolicyRequest().withRoleName(INSTANCE_IAM_ROLE_NAME).withPolicyArn(policyArn));

        iam.createInstanceProfile(
                new CreateInstanceProfileRequest().withInstanceProfileName(INSTANCE_IAM_PROFILE_NAME));
        iam.addRoleToInstanceProfile(new AddRoleToInstanceProfileRequest()
                .withInstanceProfileName(INSTANCE_IAM_PROFILE_NAME).withRoleName(INSTANCE_IAM_ROLE_NAME));
        } catch (Exception e) {}
    }
}
