FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/ussd-0.0.1-SNAPSHOT-standalone.jar /ussd/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/ussd/app.jar"]
