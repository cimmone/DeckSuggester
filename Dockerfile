# Minimal Ubuntu base image; Java and Gradle are installed explicitly below
# rather than relying on a pre-built JDK/Gradle image.
FROM ubuntu:26.04

ARG JAVA_VERSION=25
ARG GRADLE_VERSION=9.7.1

ENV JAVA_HOME=/opt/java
ENV GRADLE_HOME=/opt/gradle
ENV PATH="${JAVA_HOME}/bin:${GRADLE_HOME}/bin:${PATH}"
ENV GRADLE_USER_HOME=/root/.gradle

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Eclipse Temurin JDK (latest Java version).
RUN curl -fsSL "https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/linux/x64/jdk/hotspot/normal/eclipse" -o /tmp/jdk.tar.gz \
    && mkdir -p "${JAVA_HOME}" \
    && tar -xzf /tmp/jdk.tar.gz -C "${JAVA_HOME}" --strip-components=1 \
    && rm /tmp/jdk.tar.gz

# Install Gradle (latest version).
RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && mv "/opt/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}" \
    && rm /tmp/gradle.zip

WORKDIR /app

# Copy only the build definition first and resolve dependencies, so this
# (slow, network-bound) layer is cached independently of application source
# changes and the subsequent build can run fully offline.
COPY settings.gradle build.gradle ./
RUN gradle downloadDependencies --console=plain --no-daemon

# Now copy the actual source and build the executable jar, using the
# dependencies already cached in GRADLE_USER_HOME.
COPY src ./src
RUN gradle bootJar --console=plain --no-daemon --offline

EXPOSE 8080

CMD ["sh", "-c", "exec java -jar $(ls /app/build/libs/*.jar | head -n1)"]
