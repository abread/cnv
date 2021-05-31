package cnv.autoscaler.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

/**
 * Retrieves AWS credentials.
 * Initializes the AWSCredentialsProvider. The credentials are, by default, in `~/.aws/credentials`. Supports
 * Profile Credentials as well as Instance Profile Credentials.
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
