FROM clojure:temurin-17-tools-deps as builder

RUN apt update && \
    apt install -y ant gcc

COPY . /app

WORKDIR /app

RUN ./build-rocksaw.sh

RUN ./build.sh

FROM gcr.io/distroless/java17-debian12

COPY --from=builder /app/target/app.jar /app.jar

CMD ["/app.jar"]
