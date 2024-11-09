# Official OpenJDK runtime as a base image
FROM openjdk:17-jdk-slim as builder

# Set the working directory inside the container
WORKDIR /app

# Install sbt (Scala build tool) and sqlite3
RUN apt-get update && apt-get install -y \
  curl \
  sqlite3 \
  libsqlite3-dev \
  && curl -sL https://github.com/sbt/sbt/releases/download/v1.7.3/sbt-1.7.3.tgz | tar xz -C /usr/local \
  && ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt

# Copy the sbt build files to the container (to cache dependencies)
COPY build.sbt .
COPY project ./project

# Download dependencies (this step is cached unless build.sbt changes)
RUN sbt update

# Copy the source code to the container
COPY src ./src

# Run the application using sbt
CMD ["sbt", "run"]