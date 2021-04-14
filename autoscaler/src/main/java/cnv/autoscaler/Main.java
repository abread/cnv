package cnv.autoscaler;

public class Main {
    public static void main(String[] args) {
        try {
            AmazonDynamoDBSample.main(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}