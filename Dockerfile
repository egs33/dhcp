FROM clojure:temurin-17-tools-deps as builder

RUN apt update && \
    apt install -y ant gcc

COPY . /app

WORKDIR /app

RUN ./build-rocksaw.sh

RUN ./build.sh

FROM gcr.io/distroless/java17-debian12

WORKDIR /app

COPY --from=builder /app/target/app.jar /app/app.jar
COPY --from=builder /app/lib/librocksaw.so /app/lib/librocksaw.so

ENTRYPOINT ["java", "-Djava.library.path=lib", "-jar", "/app/app.jar"]
