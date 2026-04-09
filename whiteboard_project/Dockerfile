# Dockerfile for Whiteboard Application
FROM tomcat:9.0-jdk11-openjdk-slim

LABEL maintainer="Whiteboard Team"
LABEL version="1.0"
LABEL description="Whiteboard Application with Authentication"

# Set environment variables
ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH
ENV APP_ENVIRONMENT production
ENV DB_URL jdbc:h2:~/whiteboard_db;AUTO_SERVER=TRUE

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Remove default Tomcat apps
RUN rm -rf $CATALINA_HOME/webapps/*

# Copy WAR file
COPY target/whiteboard.war $CATALINA_HOME/webapps/ROOT.war

# Configure Tomcat users for manager
RUN echo '<?xml version="1.0" encoding="utf-8"?>\n\
<tomcat-users xmlns="http://tomcat.apache.org/xml"\n\
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n\
              xsi:schemaLocation="http://tomcat.apache.org/xml tomcat-users.xsd"\n\
              version="1.0">\n\
  <role rolename="manager-gui"/>\n\
  <role rolename="manager-script"/>\n\
  <role rolename="manager-jmx"/>\n\
  <role rolename="manager-status"/>\n\
  <user username="admin" password="admin" roles="manager-gui,manager-script,manager-jmx,manager-status"/>\n\
</tomcat-users>' > $CATALINA_HOME/conf/tomcat-users.xml

# Enable manager access from any IP
RUN sed -i 's/<Valve className="org.apache.catalina.valves.RemoteAddrValve"/<!-- <Valve className="org.apache.catalina.valves.RemoteAddrValve"/' \
    $CATALINA_HOME/webapps/manager/META-INF/context.xml && \
    sed -i 's/allow="127\.\\d+\\.\\d+\\.\\d+|::1|0:0:0:0:0:0:0:1" \/>/ -->/' \
    $CATALINA_HOME/webapps/manager/META-INF/context.xml

# Create volume for H2 database
VOLUME ["/root/whiteboard_db"]

# Expose ports
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Start Tomcat
CMD ["catalina.sh", "run"]