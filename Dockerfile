FROM bellsoft/liberica-openjdk-alpine:21
RUN mkdir -p /opt/temporal-worker

RUN addgroup -S nonroot && adduser -S nonroot -G nonroot
USER nonroot

COPY target/*.jar /opt/temporal-worker/
EXPOSE 8080
WORKDIR /opt/temporal-worker

CMD java -XX:+UseCompressedOops -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=30 -XX:MaxGCPauseMillis=10000 -XX:MaxRAMPercentage=70.0 -Xms2g -Xmx4g -jar temporal-worker.jar
