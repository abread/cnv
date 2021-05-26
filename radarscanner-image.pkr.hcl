packer {
    required_plugins {
        amazon = {
            version = ">= 0.0.1"
            source  = "github.com/hashicorp/amazon"
        }
    }
}

source "amazon-ebs" "ami-radarscanner" {
    ami_name = "ami-radarscanner"
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
    sources = ["source.amazon-ebs.ami-radarscanner"]

    provisioner "shell-local" {
        inline = ["./gradlew -p radarscanner distZip"]
    }

    provisioner "file" {
        source = "./radarscanner/build/distributions/radarscanner-1.0-SNAPSHOT.zip"
        destination = "/home/ec2-user/radarscanner.zip"
    }

    provisioner "shell" {
        inline = [
            "sudo yum update -y",
            "sudo yum install -y java-1.7.0-openjdk-headless",
            "sudo mkdir -p /opt",
            "cd /opt",
            "sudo unzip /home/ec2-user/radarscanner.zip",
            "sudo mv radarscanner-* radarscanner",
            "sudo chown -R root:nobody radarscanner",
            "sudo chmod -R g-w radarscanner",
            "echo '#!/bin/sh' | sudo tee -a /etc/rc.local",
            "echo '(cd /opt/radarscanner && runuser -u nobody -- ./bin/radarscanner -address \"0.0.0.0\" -port 8000 &)' | sudo tee -a /etc/rc.local",
            "sudo chmod +x /etc/rc.local",
            "sudo systemctl enable --now rc-local.service",
        ]
    }
}