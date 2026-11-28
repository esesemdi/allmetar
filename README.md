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

In every report the ceiling and the visibility are marked. A METAR is shown in light red when it is
a SPECI or the conditions are LIFR (ceiling below 500 ft and/or visibility below 1 statute mile); in a
TAF the groups forecasting LIFR are red. The TAF tab starts with a graph of the past seven days of
ceiling and temperature (from the METARs) continuing into the ceiling the current TAF forecasts.

Press any word of a METAR, TAF or NOTAM on a location's screen to see what it means; press and hold
to copy the whole report. On the Aerodrome tab, **Edit aerodrome data** lets you change or add
frequencies, runways and the elevation, and add your own fields (a heading and free text). Lines you
entered are marked "yours", and the built-in data can be restored at any time.

The app updates in the background at about ten past every hour (when that hour's reports have
reached the feed), checks again a few times if a report is late, and catches up as soon as the
connection returns after being offline. It also updates whenever it is opened with data older than
ten minutes. All times are UTC.

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
