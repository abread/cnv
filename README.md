# RadarScanner@Cloud

## Building
We use Gradle as our build system, which requires Java 8+ to run.
Note that Java 7-compatible bytecode will be generated, and **JRE 7 is required to run the instrumented webserver** (radarscanner).

```bash
./gradlew build`
```

`.\gradlew.bat build` should also work for Windows systems, but we have not tested this.

Tip: Use the `--parallel` flag to speed up the build

The instrumented webserver will be in `radarscanner/build/distributions/radarscanner-1.0-SNAPSHOT.zip`.

Note: building the project is not a requisite to build packer images: they do it for you.

## Subproject overview
- radarscanner - The radarscanner web service
- wsinstrumenter - The radarscanner web service instrumenting code (BIT bytecode manipulation)
- autoscaler - WIP

## Other inclusions
- `bench.sh` - a simple benchmarking script that spins up a webserver and sends 100 requests for experiment data collection
- `benchdata` - obtained experimental data (all experiments ran on LAB11 PCs without concurrent workloads)

## Deployment
Pick any availability zone and stick to it. We prefer `eu-west-2`.

1. Prepare the web server base image: `packer build radarscanner-image.pkr.hcl`
  * The resulting image will be called `ami-radarscanner`
2. Prepare the autoscaler/loadbalancer base image: `packer build autoscaler-image.pkr.hcl`
  * The resulting image will be called `ami-autoscaler`
3. (IAM console) Create a new policy `autoscaler-iam-policy`
  * Permissions:
    - PassRole (in write category) in IAM service for the resource `arn:aws:iam::<account id>:role/radarscanner-instance`
4. (IAM console) Create a new policy `radarscanner-policy`
  * Permissions: CreateTable, BatchWriteItem, PutItem, DescribeTable for the DynamoDB service on the table resource `arn:aws:dynamodb:*:<account id>:table/radarscanner-metrics`
5. (IAM console) Create a new role/instance profile `autoscaler`
  * Trusted entity type: AWS service
  * Use case: EC2
  * Policies: `autoscaler-iam-policy` (created in step 3), `AmazonEC2FullAccess` (from Amazon), `AmazonDynamoDBFullAccess` (from Amazon)
6. (IAM console) Create a new role/instance profile `radarscanner`
  * Trusted entity type: AWS service
  * Use case: EC2
  * Policies: `radarscanner-policy` (created in step 4)
7. (EC2 console) Register a keypair `cnv-aws`
  * The autoscaler will use it in the radarscanner instances it creates
8. (EC2 console) Launch a new instance `autoscaler`
  * Image: ami-autoscaler
  * Instance Type: t2.micro
  * Auto-assign public IP: Enable
  * IAM role: `autoscaler` (create in step 5)
  * Security Group: `ssh+http`
    * Allows inbound SSH and HTTP traffic from anywhere
  * Keypair: `cnv-aws` (optional)

Note: the autoscaler will create the required security group for the radarscanner instances.
If a security group with the same name already exists it will **not** be recreated with our settings.