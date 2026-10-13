# ojp-proxy

Answers [OJP 2.0](https://github.com/VDVde/OJP) `LocationInformationRequest`s by querying a
[Pelias](https://github.com/pelias/pelias) autocomplete endpoint. Built with Micronaut 5 + Maven (Java 25)
and the JAXB model from [ojp-java-model](https://github.com/opentripplanner/ojp-java-model).

## Mapping

| OJP request                           | Pelias `/v1/autocomplete`                          |
|---------------------------------------|----------------------------------------------------|
| `InitialInput/Name`                   | `text` (required)                                  |
| `InitialInput/GeoRestriction/Rectangle` | `boundary.rect.*`                                |
| `InitialInput/GeoRestriction/Circle`  | `boundary.circle.*` (radius m → km)                |
| `Restrictions/Type`                   | `layers`: stop→`stops`, address→`address`, poi→`venue`, topographicPlace→`coarse` |
| `Restrictions/NumberOfResults`        | `size`                                             |
| `Restrictions/Language` (first)       | `lang`                                             |
| `Restrictions/GeoPosition`            | `focus.point.*`                                    |

Pelias features become `PlaceResult`s: layer `stops` → `StopPlace` (ref = OTP stop id),
`address` → `Address`, `venue` → `PointOfInterest`, everything else → `TopographicPlace`.

## Usage

```sh
./mvnw test
./mvnw mn:run      # http://localhost:8080/ojp, Pelias base URL via PELIAS_URL
./mvnw package jib:dockerBuild   # builds the local image ojp-proxy:latest
docker run -p 8080:8080 -e PELIAS_URL=... ojp-proxy
curl -X POST -H 'Content-Type: application/xml' --data-binary @src/test/resources/lir-request.xml localhost:8080/ojp
```
