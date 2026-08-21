FROM dart:stable AS build

WORKDIR /source
COPY pubspec.yaml pubspec.lock ./
RUN dart pub get --enforce-lockfile
COPY analysis_options.yaml ./
COPY bin ./bin
COPY lib ./lib
RUN dart compile exe bin/relay.dart -o /output/react-web-relay

FROM gcr.io/distroless/cc-debian12:nonroot

COPY --from=build /output/react-web-relay /react-web-relay
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/react-web-relay"]
