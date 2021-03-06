# Dockerfile for STARSDK-WS
# http://www.ubique.ch/

FROM openjdk:11.0.7-jre-slim
MAINTAINER Martin Alig <alig@ubique.ch>
MAINTAINER Tobias Bachmann <bachmann@ubique.ch>
MAINTAINER Felix Haller <haller@ubique.ch>

ARG targetenv=dev

# Install ws
RUN useradd ws

WORKDIR /home/ws/

# Copy configs
COPY ./ws /home/ws

# Create skeleton
RUN mkdir -p /home/ws/bin && \
    mkdir -p /home/ws/conf && \
    mkdir -p /home/ws/archive && \
    mkdir -p /home/ws/log && \
    mkdir -p /home/ws/tmp

# Copy binary
COPY ./ws/bin/dpppt-backend-sdk-ws.jar /home/ws/bin/dpppt-backend-sdk-ws.jar

# Copy configs
ADD ./ws/conf/dpppt-backend-sdk-ws-logback-${targetenv}.xml /home/ws/conf/dpppt-backend-sdk-ws-logback.xml
ADD ./ws/conf/dpppt-backend-sdk-ws-${targetenv}.properties /home/ws/conf/dpppt-backend-sdk-ws.properties

# Copy any certificates
COPY ./ws/conf/*.pem /home/ws/conf/


RUN chown -R ws:ws /home/ws

USER ws
# Access to webinterface
EXPOSE 8080

CMD java -jar $JAVA_OPTS -Dws.origin.country=MT -Dlogging.config=/home/ws/conf/dpppt-backend-sdk-ws-logback.xml -Dspring.config.location=/home/ws/conf/dpppt-backend-sdk-ws.properties /home/ws/bin/dpppt-backend-sdk-ws.jar
