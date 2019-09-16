#Step 1: build war
FROM swr.cn-east-2.myhuaweicloud.com/greenland/builder-mvn:3-jdk-8 as build_stage
WORKDIR /build
COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY ./src /build/src
RUN mvn compile war:war


# Step 2: Run Sonarqube scan
# Gets Sonarqube Scanner from Dockerhub and runs it
FROM nikhuber/sonar-scanner:latest as sonarqube
WORKDIR /build
COPY --from=build_stage /build/src /build
RUN echo sonar.host.url=http://docker.for.mac.host.internal:9000 >> /opt/sonar-scanner-3.2.0.1227-linux/conf/sonar-scanner.properties && \
    echo "sonar.projectKey=sample:tomcat" > sonar-project.properties && echo "sonar.sources=./src" >> sonar-project.properties
CMD ["sonar-scanner"]


# Step final: Application: Make a Tomcat container and run
FROM tomcat:8-jdk8-openjdk as runnable-stage

COPY --from=build_stage /build/target/hellowar.war /usr/local/tomcat/webapps/hellowar.war

EXPOSE 8080

CMD ["catalina.sh", "run"]
#curl http://127.0.0.1:8080/hellowar/