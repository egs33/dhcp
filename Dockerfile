FROM public.ecr.aws/amazoncorretto/amazoncorretto:17 as rocksaw-builder

WORKDIR /app
RUN yum install -y wget tar patch ant gzip make gcc
COPY ./build-rocksaw.sh rocksaw-diff.patch /app/
RUN ./build-rocksaw.sh

FROM clojure:temurin-17-tools-deps as builder

WORKDIR /app
COPY ./deps.edn /app/
COPY --from=rocksaw-builder /app/lib /app/lib
RUN clojure -Sthreads 10 -X:deps prep
RUN clojure -Sthreads 10 -M:uberjar -X:deps prep
COPY . /app
RUN ./build.sh

FROM gcr.io/distroless/java17-debian12

WORKDIR /app
COPY --from=builder /app/target/app.jar /app/app.jar
COPY --from=builder /app/lib/librocksaw.so /app/lib/librocksaw.so

EXPOSE 67/udp

ENTRYPOINT ["java", "-Djava.library.path=lib", "-jar", "/app/app.jar"]
