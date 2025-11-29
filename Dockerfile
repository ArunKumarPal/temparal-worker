ARG REGISTRY_PROXY=jfrog.precisely.engineering/docker-virtual

FROM $REGISTRY_PROXY/bellsoft/liberica-openjdk-alpine:21
RUN mkdir -p /opt/bulk-processor

RUN addgroup -S nonroot && adduser -S nonroot -G nonroot
USER nonroot

COPY target/*.jar /opt/bulk-processor/
EXPOSE 8080
WORKDIR /opt/bulk-processor

CMD java -XX:+UseCompressedOops -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=30 -XX:MaxGCPauseMillis=10000 -XX:MaxRAMPercentage=70.0 -Xms2g -Xmx4g -jar bulk-processor.jar
