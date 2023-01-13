[![Build status](https://github.com/navikt/pale-2-sak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/pale-2-sak/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# Legeerklæringer journaling
This is a simple application who takes the legeerklæring document, generates a PDF and sends it to Joark to
persist it

## Technologies used
* Kotlin
* Gradle
* Ktor
* Jackson
* Junit

#### Requirements

* JDK 17

## FlowChart
This the high level flow of the application
```mermaid
  flowchart LR
        
      A[\teamsykmelding.legeerklaering/] --> pale-2-sak
      pale-2-sak <--> |Get ReceivedLegeerklaering object| B[GCP Cloud Storage bucket]
      pale-2-sak <--> |Get Legeerklaering vedleggs| B[GCP Cloud Storage bucket]
      pale-2-sak <--> pale-2-pdfgen
      pale-2-sak <--> syfohelsenettproxy
      pale-2-sak <--> dokakriv
      dokakriv --> Gosys;
```

### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the GitHub Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
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
See githubs guide [creating-a-personal-access-token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) on
how to create a personal access token.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

``` bash
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

#### Build and run tests
To build locally and run the integration tests you can simply run
``` bash
./gradlew shadowJar
 ```
or on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as
``` bash
docker build -t pale-2-sak .
 ```

#### Running a docker image
``` bash
docker run --rm -it -p 8080:8080 pale-2-sak
```

### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

``` bash
./gradlew wrapper --gradle-version $gradleVersjon
```

### Contact

This project is maintained by [navikt/teamsykmelding](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/pale-2-sak/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)