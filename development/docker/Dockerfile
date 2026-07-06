FROM maven:3-eclipse-temurin-25 as maven
# Build the source using maven, source is copied from the 'repo' build.
COPY . /usr/src/OBP-API
RUN cp /usr/src/OBP-API/pom.xml /tmp/pom.xml # For Packaging a local repository within the image
WORKDIR /usr/src/OBP-API
RUN cp src/main/resources/props/test.default.props.template src/main/resources/props/test.default.props
RUN cp src/main/resources/props/sample.props.template src/main/resources/props/default.props
RUN --mount=type=cache,target=$HOME/.m2 MAVEN_OPTS="-Xmx3G -Xss2m" mvn install RUN --mount=type=cache,target=$HOME/.m2 MAVEN_OPTS="-Xmx3G -Xss2m" mvn install -DskipTests FROM eclipse-temurin:25-jre-alpine
COPY --from=maven /usr/src/OBP-API/target/lib /app/lib
COPY --from=maven /usr/src/OBP-API/target/obp-api.jar /app/obp-api.jar
ENTRYPOINT ["java", "-jar", "/app/obp-api.jar"]