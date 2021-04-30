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

## Subproject overview
- radarscanner - The radarscanner web service
- wsinstrumenter - The radarscanner web service instrumenting code (BIT bytecode manipulation)
- autoscaler - WIP

## Other inclusions
- `bench.sh` - a simple benchmarking script that spins up a webserver and sends 100 requests for experiment data collection
- `benchdata` - obtained experimental data (all experiments ran on LAB11 PCs without concurrent workloads)

## Deployment
Pick any availability zone and stick to it. We prefer `eu-west-2`.

0. Build the project
1. Prepare the web server base image:
  1.1. Create a new t2.micro VM on EC2 running Amazon Linux (64bit x86) with:
    * at least 8GB of storage,
    * auto-assign public IP address enabled,
    * in a new security group `ssh` which allows all outbound traffic to anywhere, and all SSH inbound traffic from anywhere,
    * with some keypair you own;
  1.2. Copy the radarscanner zip using `scp` to the VM to `/home/ec2-user/radarscanner.zip`
  1.3. Run the following commands through SSH:
       ```bash
       sudo -i
       # now in the root shell:

       yum update
       yum install -y java-1.7.0-openjdk-headless
       mkdir -p /opt
       cd /opt
       unzip /home/ec2-user/radarscanner.zip
       mv radarscanner-* radarscanner
       chown -R root:nobody radarscanner
       chmod -R g-w radarscanner
       echo '(cd /opt/radarscanner && runuser -u nobody -- ./bin/radarscanner -address "0.0.0.0" -port 8000 &)' >> /etc/rc.local
       chmod +x /etc/rc.local
       systemctl enable --now rc-local.service

       # you'll need to hit Ctrl+D or input "exit<ENTER>" twice to logout
       ```
  1.4. Create an EC2 image `ami-radarscannerws` from the VM (make sure the *No reboot* option is **disabled**);
  1.5. **WAIT** for the image to have an *available* status (open the *AMIs* page to check)
  1.6. Terminate the instance after the image is created to save resources.
2. Create an EC2 launch configuration `launchcfg-radarscannerws` with:
  * AMI: `ami-radarscannerws`, created in step 1
  * EC2 instance detailed monitoring within CloudWatch enabled
  * Security group: new security group `secgrp-radarscannerws` that allows inbound TCP traffic from anywhere to port 8000
  * Keypair: none
3. Create a new EC2 classic load balancer `lb-radarscannerws` with:
  * inside default VPC
  * Enable advanced VPC configuration
  * protocol HTTP
  * load balancer port 80
  * instance protocol HTTP
  * instance port 8000
  * select the only subnet that exists
  * with a new security group `http` that allows HTTP inbound traffic from anywhere
  * Health check with ping protocol HTTP on port 8000 with path /test
4. Create a new EC2 auto scaling group `asg-radarscannerws` with:
  * Launch configuration: `launchcfg-radarscannerws`, created in step 2
  * VPC: default VPC
  * Subnet: the only one that exists
  * Attach to load balancer `lb-radarscannerws`, created in step 3
  * ELB health checks enabled
  * Desired/minimum/maximum capacity: at least 1
    + **NOTE**: make maximum capacity at least 2 to be able to observe system scaling up/down
5. Create the alarm `alarm-asg-radarscannerws-highcpu` in CloudWatch:
  * Metric: CPUUtilization (by Auto Scaling Group, selecting `asg-radarscannerws` from step 4)
  * Statistics: Average
  * Period: 1min
  * Threshold: static, greater than 50 (%)
  * Notification: none
6. Create the alarm `alarm-asg-radarscannerws-lowcpu` in CloudWatch:
  * Metric: CPUUtilization (by Auto Scaling Group, selecting `asg-radarscannerws` from step 4)
  * Statistics: Average
  * Period: 1min
  * Threshold: static, lower than 40 (%)
  * Notification: none
7. Create step scaling policy `asg-radarscannerws-scaleup` in `asg-radarscannerws`:
  * CloudWatch alarm: `alarm-asg-radarscannerws-highcpu`
  * Action: Add 1 capacity unit (when 50 <= CPUUtilization < +infinity)
8. Create step scaling policy `asg-radarscannerws-scaledown` in `asg-radarscannerws`:
  * CloudWatch alarm: `alarm-asg-radarscannerws-lowcpu`
  * Action: Remove 1 capacity unit (when 40 >= CPUUtilization < +infinity)
