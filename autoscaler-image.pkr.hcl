packer {
    required_plugins {
        amazon = {
            version = ">= 0.0.1"
            source  = "github.com/hashicorp/amazon"
        }
    }
}

source "amazon-ebs" "ami-autoscaler" {
    ami_name = "ami-autoscaler"
    instance_type = "t2.micro"
    region = "eu-west-2"

    source_ami_filter {
        filters = {
            name = "amzn2-ami-hvm-2.0.20210427.0-x86_64-gp2"
            root-device-type = "ebs"
            virtualization-type = "hvm"
        }
        owners = ["amazon"]
        most_recent = true
    }

    ssh_username = "ec2-user"
}

build {
    sources = ["source.amazon-ebs.ami-autoscaler"]

    provisioner "shell-local" {
        inline = ["./gradlew -p autoscaler distZip"]
    }

    provisioner "file" {
        source = "./autoscaler/build/distributions/autoscaler-1.0-SNAPSHOT.zip"
        destination = "/home/ec2-user/autoscaler.zip"
    }

    provisioner "shell" {
        inline = [
        <<EOF
            sudo yum update -y
            sudo yum install -y java-1.8.0-openjdk-headless
            sudo mkdir -p /opt
            cd /opt
            sudo unzip /home/ec2-user/autoscaler.zip
            sudo mv autoscaler-* autoscaler
            sudo chown -R root:nobody autoscaler
            sudo chmod -R g-w autoscaler
            echo '#!/bin/sh' | sudo tee -a /etc/rc.local
            echo 'sysctl net.ipv4.ip_unprivileged_port_start=80' | sudo tee -a /etc/rc.local
            echo 'sysctl net.ipv6.ip_unprivileged_port_start=80' | sudo tee -a /etc/rc.local
            echo '' | sudo tee -a /etc/rc.local
            echo "(cd /opt/autoscaler && runuser -u nobody -- env JAVA_OPTS=\"-Dlb.address=0.0.0.0 -Dlb.port=80\" ./bin/autoscaler &)" | sudo tee -a /etc/rc.local
            sudo chmod +x /etc/rc.local
            sudo systemctl enable rc-local.service
        EOF
        ]
    }
}