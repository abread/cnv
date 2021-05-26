package cnv.autoscaler.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

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

    static AWSCredentialsProvider credentialsProvider() {
        return CREDENTIALS_PROVIDER;
    }
}
