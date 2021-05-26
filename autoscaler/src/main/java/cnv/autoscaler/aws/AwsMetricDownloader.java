package cnv.autoscaler.aws;

/*
 * Copyright 2012-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
import java.util.HashMap;
import java.util.OptionalDouble;
import java.util.logging.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import cnv.autoscaler.loadbalancer.RequestParams;

/**
 * This sample demonstrates how to perform a few simple operations with the
 * Amazon DynamoDB service.
 */
public class AwsMetricDownloader {
    private final static Logger LOGGER = Logger.getLogger(AwsMetricDownloader.class.getName());

    public final static int POSITION_THRESHOLD = 16; // TODO: tune (future work)
    static AmazonDynamoDB dynamoDB;

    private static final String METRICS_TABLE_NAME = System.getProperty("mss.dynamodb.tablename",
            "radarscanner-metrics");

    static {

        dynamoDB = AmazonDynamoDBClientBuilder.standard().withCredentials(AwsCredentials.credentialsProvider())
                .withRegion("eu-west-2").build();

        // Create a table with a primary hash key named 'name', which holds a string
        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(METRICS_TABLE_NAME)
                .withKeySchema(new KeySchemaElement().withAttributeName("name").withKeyType(KeyType.HASH))
                .withAttributeDefinitions(
                        new AttributeDefinition().withAttributeName("name").withAttributeType(ScalarAttributeType.S))
                .withProvisionedThroughput(
                        new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

        // Create table if it does not exist yet
        TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);

        try {
            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, METRICS_TABLE_NAME);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static OptionalDouble getEstimatedMethodCountForRequest(RequestParams params) {
        try {
            HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();

            Condition strategyCondition = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
                    .withAttributeValueList(new AttributeValue().withS(params.algo));
            scanFilter.put("args.strategy", strategyCondition);
            Condition imageCondition = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
                    .withAttributeValueList(new AttributeValue().withS("datasets/" + params.imagePath));
            scanFilter.put("args.imagePath", imageCondition);
            addIntervalCondition(scanFilter, "args.x0", params.x0);
            addIntervalCondition(scanFilter, "args.x1", params.x1);
            addIntervalCondition(scanFilter, "args.y0", params.y0);
            addIntervalCondition(scanFilter, "args.y1", params.y1);

            ScanRequest scanRequest = new ScanRequest(METRICS_TABLE_NAME).withScanFilter(scanFilter);
            ScanResult scanResult = dynamoDB.scan(scanRequest);
            return scanResult.getItems().stream().mapToDouble(m -> Double.parseDouble(m.get("methodCount").getN()))
                    .average();
        } catch (AmazonClientException ignored) {
            LOGGER.warning("Failed to get better estimate for request: " + ignored.getMessage());
            return OptionalDouble.empty();
        }
    }

    private static void addIntervalCondition(HashMap<String, Condition> filter, String arg, long value) {
        Condition between = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN.toString())
                .withAttributeValueList(new AttributeValue().withN(Long.toString(value - POSITION_THRESHOLD)),
                        new AttributeValue().withN(Long.toString(value + POSITION_THRESHOLD)));
        filter.put(arg, between);
    }

}