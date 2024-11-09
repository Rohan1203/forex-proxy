# Forex Proxy Interpreter for OneFrame

## Features
- MVC Architecture
- Logging
    - Time based log rotation
    - Size based log rotation (optional you can enable)
- Caching (SQLite) with auto cleanup scheduler
- Circuit breaker for API fail case


## Explanation
### Caching
- Used SQLite as caching as it is so light, building and managing the configuration gets easy.
- Maintained over file system and no service management(like redis as a separate service)

## APIs
```
- [GET] /health
- [GET] /rates?from=<currency>&to=<currency> (3 digit currency code; eg: INR, USD, JPY)
```

## Run application in local (using sbt)
### Install Dependencies
``` bash
$ sbt clean compile
```

### Run application
``` bash
$ sbt run
```

## Run application in local (using docker)
[NOTE] But this won't connect to oneframe (as different local network)
``` bash
$ docker build -t forex-proxy:dev .
$ docker run -p 8082:8082 forex-proxy:dev
```
### so, use docker-compose to create a common network
[NOTE][MUST] Change the oneframe uri in application config before running docker compose (as localhost resembles to alias(oneframe))
Ex.
```
oneframe.uri = uri = "http://localhost:8080/rates" (current)
to
oneframe.uri = "http://oneframe:8080/rates" #to use in docker-network
```
``` bash
$ docker compose up
```

## Limitation
- forex-proxy interpreter doesn't support multi currency pair as of now.