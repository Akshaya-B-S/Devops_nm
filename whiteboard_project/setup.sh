#!/bin/bash

echo "========================================="
echo "Setting up Whiteboard Application"
echo "========================================="

# Start Docker containers
echo "Starting Docker containers..."
docker-compose up -d

# Wait for services
echo "Waiting for services to start..."
sleep 30

# Build the application
echo "Building application..."
mvn clean package

# Run SonarQube analysis
echo "Running SonarQube analysis..."
mvn sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=sqp_9c142b0b4b14673f080854151da382bdda91989c

# Deploy to Tomcat
echo "Deploying to Tomcat..."
curl -u admin:admin -T target/whiteboard.war \
  "http://localhost:8080/manager/text/deploy?path=/whiteboard&update=true"

# Test the deployment
echo "Testing deployment..."
sleep 10
curl -f http://localhost:8080/whiteboard/health

echo "========================================="
echo "Setup Complete!"
echo "========================================="
echo "Access URLs:"
echo "Application: http://localhost:8080/whiteboard"
echo "Login: http://localhost:8080/whiteboard/login.html"
echo "Register: http://localhost:8080/whiteboard/register.html"
echo "SonarQube: http://localhost:9000 (admin/admin)"
echo "Jenkins: http://localhost:8080"
echo "========================================="