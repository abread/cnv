package cnv.autoscaler.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

/**
 * Retrieves AWS credentials.
 * Provides an AWSCredentialsProvider instnace. It first attempts to load credentials
 * from a file (~/.aws/credentials), and if not present it falls back to loading instance
 * profile credentials.
 */
class AwsCredentials {
    private static final AWSCredentialsProvider CREDENTIALS_PROVIDER;

    static {
        AWSCredentialsProvider provider;

        try {
            provider = new ProfileCredentialsProvider();
            provider.getCredentials();
        } catch (Exception ignored) {
            provider = InstanceProfileCredentialsProvider.getInstance();
        }

        try {
            provider.getCredentials();
        } catch (Exception e) {
            throw new RuntimeException("Failed to procure AWS Credentials", e);
        }

        CREDENTIALS_PROVIDER = provider;
    }

    /**
     * @return the AWSCredentialsProvider after set up
     */
    static AWSCredentialsProvider credentialsProvider() {
        return CREDENTIALS_PROVIDER;
    }
}
