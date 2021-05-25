package pt.ulisboa.tecnico.cnv.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
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
    private MetricUploaderTask uploaderTask;

    public MetricUploader() throws InterruptedException, TableNeverTransitionedToStateException {
        this.dynamoDBClient = createDynamoDBClient();
        ensureTableActive(this.dynamoDBClient, this.tableName);

        this.uploaderTask = new MetricUploaderTask(tableName, dynamoDBClient);
        new Thread(this.uploaderTask).start();
    }

    private static AmazonDynamoDB createDynamoDBClient() {
        AWSCredentialsProvider credentialsProvider;
        try {
            credentialsProvider = new ProfileCredentialsProvider();
            credentialsProvider.getCredentials();
        } catch (Exception ignored) {
            credentialsProvider = InstanceProfileCredentialsProvider.getInstance();
        }

        return AmazonDynamoDBClientBuilder.standard().withCredentials(credentialsProvider)
                .withRegion(System.getProperty("mss.dynamodb.region", "eu-west-2")).build();
    }

    private static void ensureTableActive(AmazonDynamoDB client, String tableName)
            throws InterruptedException, TableNeverTransitionedToStateException {
        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                .withKeySchema(new KeySchemaElement().withAttributeName("id").withKeyType(KeyType.HASH))
                .withAttributeDefinitions(
                        new AttributeDefinition().withAttributeName("id").withAttributeType(ScalarAttributeType.S))
                .withProvisionedThroughput(
                        new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

        // Create table if it does not exist yet
        TableUtils.createTableIfNotExists(client, createTableRequest);
        // wait for the table to move into ACTIVE state
        TableUtils.waitUntilActive(client, tableName);
    }

    public void upload(Metrics metrics) {
        this.uploaderTask.enqueue(metrics);
    }

    private static class MetricUploaderTask implements Runnable {
        private AmazonDynamoDB dynamoDBClient;
        private String tableName;
        private ConcurrentLinkedQueue<Metrics> uploadQueue = new ConcurrentLinkedQueue<>();
        private Semaphore queueNotEmpty = new Semaphore(1);
        private int MAX_QUEUE_SIZE = 32;

        public MetricUploaderTask(String tableName, AmazonDynamoDB dynamoDBClient) {
            this.tableName = tableName;
            this.dynamoDBClient = dynamoDBClient;
        }

        public void run() {
            while (true) {
                Metrics metrics;
                while ((metrics = uploadQueue.poll()) != null) {
                    try {
                        Map<String, AttributeValue> item = prepareMetrics(metrics);
                        PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
                        dynamoDBClient.putItem(putItemRequest);
                    } catch (Exception e) {
                        System.err.println("Could not upload metric: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                try {
                    queueNotEmpty.acquire();
                } catch (InterruptedException ignored) {
                }
            }
        }

        /**
         * Never blocks.
         *
         * @param metrics
         */
        public void enqueue(Metrics metrics) {
            if (uploadQueue.size() > MAX_QUEUE_SIZE) {
                // try to keep queue with a reasonable size
                System.err.println("Discarding metrics: too many items in queue");
            } else {
                uploadQueue.add(metrics);
                queueNotEmpty.release();
            }
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

}
