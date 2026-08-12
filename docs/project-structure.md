# Project structure

```text
I Need Air/
├── .env                    local secrets; ignored by Git
├── .env.example            safe template for local configuration
├── docs/                   setup, API, release, and project notes
├── recommendation-model/   offline recommendation-model experiments
├── scripts/                developer and release automation
├── src/
│   └── main/
│       ├── java/com/ineedair/
│       │   ├── client/     external API clients
│       │   ├── config/     app and database configuration
│       │   ├── model/      immutable application data models
│       │   ├── service/    recommendation, itinerary, and business logic
│       │   ├── store/      SQLite cache and favourites repository
│       │   └── ui/         JavaFX navigation and controllers
│       └── resources/
│           ├── application.properties
│           └── com/ineedair/ui/  FXML, CSS, and map HTML
├── pom.xml                 Maven build and dependencies
├── mvnw / mvnw.cmd         Maven Wrapper
└── README.md               quick start
```

Generated files such as `target/`, local databases, downloaded model data, `.tools/`, and `.env` stay out of Git.
