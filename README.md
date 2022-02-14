[![Build status](https://github.com/navikt/pale-2-sak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/pale-2-sak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# Legeerklæringer journaling
This is a simple application who takes the legeerklæring document, generates a PDF and sends it to Joark to
persist it

## Technologies used
* Kotlin
* Gradle
* Ktor
* Jackson
* Spek

#### Requirements

* JDK 17

### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/pale-2-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

#### Build and run tests
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t pale-2-sak .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 pale-2-sak`