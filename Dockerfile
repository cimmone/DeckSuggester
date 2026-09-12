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
    && apt-get install -y --no-install-recommends ca-certificates curl unzip gnupg \
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

# Install MongoDB Community Server + the database tools (mongoimport etc).
# There is no apt repo yet for this Ubuntu release, so we use the "noble"
# (24.04) repo, which is binary-compatible.
RUN curl -fsSL https://pgp.mongodb.com/server-8.0.asc | gpg --dearmor -o /usr/share/keyrings/mongodb-server-8.0.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg] https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" > /etc/apt/sources.list.d/mongodb-org-8.0.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends mongodb-org-server mongodb-database-tools \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy only the build definition first and resolve dependencies, so this
# (slow, network-bound) layer is cached independently of application source
# changes and the subsequent build can run fully offline.
COPY settings.gradle build.gradle ./
RUN gradle downloadDependencies --console=plain --no-daemon

# Fetch the Scryfall "all cards" bulk data export and load it into Mongo at
# build time, so the finished image already contains a populated database and
# no download/import is needed on every container start.
#
# This downloads a fresh export from Scryfall on every build. The URL's
# timestamp changes on every Scryfall export, so it needs updating whenever
# a new export is published (check https://scryfall.com/docs/api/bulk-data).
# Scryfall rate-limits frequent bulk data requests, which becomes a problem
# while iterating on the Dockerfile - while doing that, comment out the ARG
# and RUN lines below and uncomment this COPY of a local download instead:
#
# COPY all-cards.jsonl.gz /app/data/all-cards.jsonl.gz
ARG SCRYFALL_ALL_CARDS_URL=https://data.scryfall.io/all-cards/all-cards-20260912091715.jsonl.gz
RUN mkdir -p /app/data \
    && curl -fsSL "${SCRYFALL_ALL_CARDS_URL}" -o /app/data/all-cards.jsonl.gz

RUN mkdir -p /data/db \
    && mongod --dbpath /data/db --bind_ip 127.0.0.1 --fork --logpath /var/log/mongod-import.log \
    && zcat /app/data/all-cards.jsonl.gz | mongoimport --uri mongodb://localhost:27017/scryfall --collection cards --numInsertionWorkers 4 \
    && mongod --dbpath /data/db --shutdown \
    && rm /app/data/all-cards.jsonl.gz

# Now copy the actual source and build the executable jar, using the
# dependencies already cached in GRADLE_USER_HOME.
COPY src ./src
RUN gradle bootJar --console=plain --no-daemon --offline

EXPOSE 8080

# Starts the pre-populated Mongo instance in the background, then runs the
# Spring Boot app in the foreground.
CMD ["sh", "-c", "mongod --dbpath /data/db --bind_ip 127.0.0.1 --fork --logpath /var/log/mongod.log && exec java -jar $(ls /app/build/libs/*.jar | head -n1)"]
