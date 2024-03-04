FROM maven:3.9.6-eclipse-temurin-17 as build

WORKDIR /app

COPY . .

RUN mvn package -DskipTests

# Based on https://github.com/tonda100/wildfly-postgresql
FROM quay.io/wildfly/wildfly:26.1.2.Final-jdk17 as production

ENV WILDFLY_HOME /opt/jboss/wildfly

ENV DATASOURCE_NAME ApplicationDS
ENV DATASOURCE_JNDI java:/SymphonyDS

ENV DB_HOST database
ENV DB_PORT 5432
ENV DB_USER user
ENV DB_PASS password
ENV DB_NAME symphony

# create temporary deployment dir, because wars can deploy after the datasource is created
USER jboss
RUN mkdir /tmp/deployments
ENV DEPLOY_DIR /tmp/deployments

RUN mkdir /tmp/jboss-cli
ENV CLI_DIR /tmp/jboss-cli

COPY startWithPostgres.sh $WILDFLY_HOME/bin

USER root
RUN chown jboss:jboss $WILDFLY_HOME/bin/startWithPostgres.sh
# RUN chown -R jboss:jboss $CLI_DIR
RUN chmod 755 $WILDFLY_HOME/bin/startWithPostgres.sh
USER jboss

RUN wget -O /tmp/postgresql-42.2.28.jar https://jdbc.postgresql.org/download/postgresql-42.2.28.jre7.jar

COPY --from=build /app/target/symphony-ws-1.19.0-SNAPSHOT.war /tmp/deployments/

ENTRYPOINT $WILDFLY_HOME/bin/startWithPostgres.sh
