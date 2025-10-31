# Project Overview: Forex Proxy Service

Architecture:
•  Functional Scala using Cats Effect, Http4s, and Circe
•  Layered architecture: Domain → Services → Programs → HTTP Routes
•  Effect system: IO monad for side effects
•  Current functionality: Returns dummy forex rates for currency pairs

Key Components:

1. Domain Layer (forex.domain)
◦  Currency: 9 supported currencies (USD, EUR, GBP, JPY, etc.)
◦  Rate: Exchange rate with pair, price, and timestamp
◦  Price, Timestamp: Value objects
2. Services Layer (forex.services)
◦  OneFrameDummy: Currently returns hardcoded rate of 100 for any pair
3. Programs Layer (forex.programs)
◦  Business logic that coordinates services
◦  Error handling transformation
4. HTTP Layer (forex.http)
◦  REST endpoint: GET /rates?from=USD&to=EUR
◦  JSON serialization via Circe
5. Configuration
◦  Host: 0.0.0.0, Port: 8080
◦  Timeout: 40 seconds

Extension Possibilities:

✅ Yes, this is highly extensible:

1. Replace dummy service with real forex API integration
2. Add caching layer (Redis/in-memory) to reduce API calls
3. Add rate limiting middleware
4. Add authentication/authorization
5. Add database persistence (Doobie for PostgreSQL)
6. Add historical rates endpoint
7. Add batch rate queries
8. Add WebSocket support for real-time rates
9. Add metrics/monitoring (Prometheus)
10. Add more currencies or cryptocurrency support



┌─────────────────────────────────────────────────────────────────┐
│                         MAIN.SCALA                              │
│  Entry Point - Bootstraps Application with IOApp               │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      APPLICATION CLASS                          │
│  • Loads Config                                                 │
│  • Creates HTTP Client (BlazeClientBuilder)                     │
│  • Initializes Module                                           │
│  • Starts HTTP Server (BlazeServerBuilder)                      │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                         MODULE.SCALA                            │
│  Dependency Injection & Wiring                                  │
│  ┌────────────────────────────────────────────┐                │
│  │ 1. RatesService (Live/Dummy)               │                │
│  │    ↓                                       │                │
│  │ 2. RatesProgram                            │                │
│  │    ↓                                       │                │
│  │ 3. RatesHttpRoutes & HealthHttpRoutes      │                │
│  │    ↓                                       │                │
│  │ 4. HttpApp (with Middleware)               │                │
│  └────────────────────────────────────────────┘                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      HTTP LAYER (http/)                         │
│                                                                 │
│  ┌──────────────────────────────────────────┐                  │
│  │     RatesHttpRoutes.scala                │                  │
│  │  • GET /rates?from=X&to=Y (single)       │                  │
│  │  • GET /rates?pair=X-Y&pair=A-B (batch)  │                  │
│  │  • Access Logging                        │                  │
│  │  • Uses: QueryParams, Converters         │                  │
│  └────────────┬─────────────────────────────┘                  │
│               │                                                 │
│  ┌────────────▼─────────────────────────────┐                  │
│  │     Protocol.scala                       │                  │
│  │  • GetApiResponse (with nested rates{})  │                  │
│  │  • GetBatchApiResponse                   │                  │
│  │  • RatesInfo (bid, ask, price)           │                  │
│  │  • JSON Encoders                         │                  │
│  └──────────────────────────────────────────┘                  │
│               │                                                 │
│  ┌────────────▼─────────────────────────────┐                  │
│  │     Converters.scala                     │                  │
│  │  Rate → GetApiResponse                   │                  │
│  └──────────────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   PROGRAMS LAYER (programs/)                    │
│                                                                 │
│  ┌──────────────────────────────────────────┐                  │
│  │     rates/Program.scala                  │                  │
│  │  • Business Logic Layer                  │                  │
│  │  • Error Mapping                         │                  │
│  │  • Logging                               │                  │
│  │  • Calls RatesService                    │                  │
│  └────────────┬─────────────────────────────┘                  │
│               │                                                 │
│  ┌────────────▼─────────────────────────────┐                  │
│  │     rates/Protocol.scala                 │                  │
│  │  GetRatesRequest(from, to)               │                  │
│  └──────────────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   SERVICES LAYER (services/)                    │
│                                                                 │
│  ┌──────────────────────────────────────────┐                  │
│  │     package.scala                        │                  │
│  │  • RatesService type alias               │                  │
│  │  • RatesServices.live / .dummy           │                  │
│  └────────────┬─────────────────────────────┘                  │
│               │                                                 │
│  ┌────────────▼─────────────────────────────┐                  │
│  │     rates/Interpreters.scala             │                  │
│  │  • Factory for Service Implementations   │                  │
│  └────────────┬─────────────────────────────┘                  │
│               │                                                 │
│     ┌─────────┴──────────┐                                     │
│     ▼                    ▼                                     │
│  ┌──────────────┐  ┌──────────────────────┐                   │
│  │OneFrameDummy │  │  OneFrameClient      │                   │
│  │  (Mock)      │  │  (Live API)          │                   │
│  │              │  │  • HTTP Request      │                   │
│  │              │  │  • OneFrameResponse  │                   │
│  │              │  │  • Decoder           │                   │
│  └──────────────┘  └──────────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DOMAIN LAYER (domain/)                       │
│  • Rate.scala (Rate, Rate.Pair)                                │
│  • Currency.scala                                               │
│  • Price.scala                                                  │
│  • Timestamp.scala                                              │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    LOGGING (logging/)                           │
│  ┌──────────────────────────────────────────┐                  │
│  │     Loggers.scala                        │                  │
│  │  • AccessLogger (access.log)             │                  │
│  │  • DbLogger (database.log)               │                  │
│  │  • CacheLogger (cache.log)               │                  │
│  └──────────────────────────────────────────┘                  │
│                                                                 │
│  ┌──────────────────────────────────────────┐                  │
│  │     logback.xml (resources/)             │                  │
│  │  • Separate appenders per logger         │                  │
│  │  • JSON structured logging               │                  │
│  └──────────────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  CONFIG LAYER (config/)                         │
│  • ApplicationConfig.scala                                      │
│  • OneFrameConfig (uri, token, timeout)                         │
│  • HttpConfig (host, port, timeout)                             │
└─────────────────────────────────────────────────────────────────┘