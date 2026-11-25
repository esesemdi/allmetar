# AllMetar

A minimalist Android app for aviation weather: up to 10 saved aerodromes, each with its current
METAR, recent and week-long METAR history, TAFs, frequencies, runways and (for Canada) NOTAMs.
Includes a 4x1 home-screen widget showing the current METAR of one saved location.

**Not an official briefing source.** Always confirm weather, NOTAMs and aerodrome data with
NAV CANADA / FAA sources and current publications (CFS, Chart Supplement) before flight.

## Install

1. On your phone, open the [latest release](../../releases/latest) and download `allmetar.apk`.
2. Open the file and allow your browser to install apps when Android asks.

To add the widget: long-press the home screen, choose Widgets, find AllMetar, place it and pick a location.
Long-press the widget to change its location later.

## What it shows

| Section | Content | Source |
| --- | --- | --- |
| List | Current METAR of each saved location | aviationweather.gov |
| METAR | 5 latest reports, plus one report every 3 hours for the past 7 days | aviationweather.gov |
| TAF | Current TAF and the ones issued in the previous 36 hours | aviationweather.gov |
| Aerodrome | Frequencies, runways, elevation | OurAirports (bundled, community data) |
| Aerodrome | NOTAMs (Canadian aerodromes) | NAV CANADA CFPS |

The app updates in the background a few minutes past every hour, and whenever it is opened with
data older than ten minutes. All times are UTC.

US NOTAMs are not shown: the FAA does not offer an open NOTAM feed. The app links to the FAA
NOTAM Search site instead.

## Building

Every push to `main` is built by GitHub Actions and published as a release.

For updates to install over the previous version, builds must be signed with the same key. Store a
PKCS12 keystore (alias `allmetar`, password `allmetar`), base64 encoded, in the repository secret
`SIGNING_KEYSTORE_B64`. Without it each build is signed with a throwaway key.

To build locally, open the folder in Android Studio or run `./gradlew assembleRelease`.

## Refreshing the aerodrome data

```
git clone --depth 1 https://github.com/davidmegginson/ourairports-data
python3 tools/build_airports.py ourairports-data
```

This rewrites `app/src/main/assets/airports.tsv` (Canada and US; airports and seaplane bases).
