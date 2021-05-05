package pt.ulisboa.tecnico.cnv.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.dynamodbv2.util.TableUtils.TableNeverTransitionedToStateException;

import pt.ulisboa.tecnico.cnv.server.MetricTracker.Metrics;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;

public class MetricUploader {
    private AmazonDynamoDB dynamoDBClient;
    private String tableName = System.getProperty("mss.dynamodb.tablename", "radarscanner-metrics");

    public MetricUploader() throws InterruptedException, TableNeverTransitionedToStateException {
        this.dynamoDBClient = createDynamoDBClient();
        ensureTableActive(this.dynamoDBClient, this.tableName);
    }

    private static AmazonDynamoDB createDynamoDBClient() {
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        try {
            credentialsProvider.getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
                    + "Please make sure that your credentials file is at the correct "
                    + "location (~/.aws/credentials), and is in valid format.", e);
        }

        return AmazonDynamoDBClientBuilder.standard().withCredentials(credentialsProvider)
                .withRegion(System.getProperty("mss.dynamodb.region", "eu-west-2")).build();
    }

    private static void ensureTableActive(AmazonDynamoDB client, String tableName)
            throws InterruptedException, TableNeverTransitionedToStateException {
        CreateTableRequest createTableRequest = new CreateTableRequest()
            .withTableName(tableName)
            .withKeySchema(new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH))
            .withAttributeDefinitions(new AttributeDefinition().withAttributeName("id")
                    .withAttributeType(ScalarAttributeType.S))
            .withProvisionedThroughput(
                new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

        // Create table if it does not exist yet
        TableUtils.createTableIfNotExists(client, createTableRequest);
        // wait for the table to move into ACTIVE state
        TableUtils.waitUntilActive(client, tableName);
    }

    public void upload(Metrics metrics) throws Exception {
        Map<String, AttributeValue> item = prepareMetrics(metrics);
        PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
        this.dynamoDBClient.putItem(putItemRequest);
    }

    private static Map<String, AttributeValue> prepareMetrics(Metrics metrics) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        SolverArgumentParser requestArgs = new SolverArgumentParser(metrics.requestParams);

        item.put("id", new AttributeValue().withS(UUID.randomUUID().toString()));
        item.put("args.width", new AttributeValue().withN(requestArgs.getWidth().toString()));
        item.put("args.height", new AttributeValue().withN(requestArgs.getHeight().toString()));
        item.put("args.startx", new AttributeValue().withN(requestArgs.getStartX().toString()));
        item.put("args.starty", new AttributeValue().withN(requestArgs.getStartY().toString()));
        item.put("args.x0", new AttributeValue().withN(requestArgs.getX0().toString()));
        item.put("args.y0", new AttributeValue().withN(requestArgs.getY0().toString()));
        item.put("args.x1", new AttributeValue().withN(requestArgs.getX1().toString()));
        item.put("args.y1", new AttributeValue().withN(requestArgs.getY1().toString()));
        item.put("args.strategy", new AttributeValue().withS(requestArgs.getSolverStrategy().toString()));
        item.put("args.imagePath", new AttributeValue().withS(requestArgs.getInputImage()));
        item.put("methodCount", new AttributeValue().withN(Long.toString(metrics.methodCount)));

        return item;
    }
}
