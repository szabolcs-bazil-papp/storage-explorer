# Storage example creator

Spins up the smartbit4all platform on a `StorageFS` in a target folder, persists a fixed set of
animal shelter objects ("Paws and Whiskers") and shuts down, leaving a reference storage behind.

## Run

    gradlew :shelter-storage-creator:run --args="--output=build/reference --versions=classic --force"

| Option | Meaning |
|---|---|
| `--output=<folder>` | target folder (default `build/reference-storage`); refuses a non-empty folder without `--force` |
| `--versions=indexed\|classic` | `indexed`: `.iv` segment files (platform default); `classic`: one file per version |
| `--index-size=<n>` | versions per `.iv` segment (default 256) |
| `--force` | wipe the output folder first |

The platform is consumed from `../platform` as a composite build. The domain is defined in
`domain/src/main/resources/descriptors/shelter-api.yaml`; the model is generated into
`domain/src-gen` on compile (`runGenAllOnCompile`).

## What is created

The content (objects and their quantities) is the same on every run. URIs and timestamps are
minted by the platform and differ.

| Kind | Content |
|---|---|
| Plain versioned objects (`shelter`) | 2 Shelter, 6 Dog, 5 Cat, 4 Adopter, 5 AdoptionContract, 2 Veterinarian, 6 AdoptionFile (the relation holder, see below); adopters, shelters and Biscuit the dog are modified (Biscuit 300 times, so indexed mode spans two `.iv` files) |
| Plain single-version objects (`shelterrecords`) | 12 VetVisit |
| StoredList | global `waitingForAHome`, `allVetVisits`; scoped: shelter `residentDogs`/`residentCats`, dog `vetVisits` |
| StoredMap | global `dogsByChip`, `veterinariansByClinic`; scoped: adopter `petsByNickname` |
| StoredReference | global `mascot` (modified once); scoped: shelter `shelterVet`, adopter `favoriteCat` |
| TableData (`.td`) | `feeding-log` (60 rows), `treat-log` (8 rows) |
| ObjectStreams (`.os`) | shelter `eventLog` (40 / 25 entries), shelter `nightWatch` (12), dog `healthLog` (30 and 10, appended in two rounds) |

The storage strategy is applied to every `Storage` scheme through a `BeanPostProcessor`, before
anything is saved.

## Relations

`AdoptionFile` is the domain type that carries relations only: a reference to the adopter, a
composite reference to the contract, lists of dogs, cats and vet visits and a map of veterinarians
by clinic. `ShelterConfig.shelterReferences()` is its `ObjectReferenceConfigs` bean.

Each file is saved through `Storage`, so the relations are present twice: as URI properties of the
object (what the configs describe) and as storage level relations in the `.r` file next to the
version files (two references and the collections `dogs`, `cats`, `vetVisits`, `veterinarians`).
Saving through `ObjectApi` alone does not write `.r` files.
