# Dockerfile for STARSDK-WS
# http://www.ubique.ch/

FROM openjdk:11-jre-slim
MAINTAINER Malcolm Spiteri <malcolm.spiteri@gov.mt>

# Install OpenSSH and set the password for root to "Docker!"
RUN apt update \ 
    && apt -y install openssh-server dnsutils curl \
    && echo "root:Docker!" | chpasswd

# Copy the sshd_config file to the /etc/ssh/ directory
COPY sshd_config /etc/ssh/

ARG targetenv=dev

# Install ws
# RUN useradd ws

RUN mkdir /opt/ws

WORKDIR /opt/ws/

# Copy configs
COPY ./ws /opt/ws

# Create skeleton
RUN mkdir -p /opt/ws/bin && \
    mkdir -p /opt/ws/conf && \
    mkdir -p /opt/ws/archive && \
    mkdir -p /opt/ws/log && \
    mkdir -p /opt/ws/tmp

# Copy binary
COPY ./ws/bin/dpppt-backend-sdk-interops-efgs.jar /opt/ws/bin/dpppt-backend-sdk-interops-efgs.jar

# Copy configs
ADD ./ws/conf/dpppt-backend-sdk-ws-logback-${targetenv}.xml /opt/ws/conf/dpppt-backend-sdk-ws-logback.xml
ADD ./ws/conf/dpppt-backend-sdk-ws-${targetenv}.properties /opt/ws/conf/dpppt-backend-sdk-ws.properties

# Copy key stores
COPY ./ws/conf/*.p12 /opt/ws/conf/
COPY ./ws/conf/*.jks /opt/ws/conf/

# RUN chown -R ws:ws /opt/ws

USER root
# Access to webinterface and SSH
EXPOSE 8080 2222

CMD mkdir -p -m0755 /run/sshd && /usr/sbin/sshd && java -jar $JAVA_OPTS -Dws.origin.country=MT -Dlogging.config=/opt/ws/conf/dpppt-backend-sdk-ws-logback.xml -Dspring.config.location=/opt/ws/conf/dpppt-backend-sdk-ws.properties /opt/ws/bin/dpppt-backend-sdk-interops-efgs.jar
