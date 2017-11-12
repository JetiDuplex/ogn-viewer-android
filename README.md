# OGN Viewer

## Description
Android app that visualizes the aircraft traffic from [The Open Glider Network project (OGN)](http://glidernet.org) on a map.

This is a fork of meisterschuelers ogn-viewer-android app. (https://github.com/Meisterschueler/ogn-viewer-android)

Current version: 1.3.2

See [release notes](release-notes.md) for details.

## Dependencies
The app uses two repositories from glidernet:
https://github.com/glidernet/ogn-commons-java

and

https://github.com/glidernet/ogn-client-java

Clone the Java7 files from "Android" branch!

You need Eclipse and Maven to build the jar files.

Use "maven install" to build them.

## Building
Use Android Studio to build the app.

Replace the google_maps_key in google_maps_api.xml with your own values.

Go to https://console.developser.google.com and sign in with your credentials.

Add a new project for ogn-viewer.

Generate a new api key and copy it to the xml file.

Add the package name "com.meisterschueler.ognviewer" to your api key.

You need your own SHA1 fingerprint too.

Use `keytool -list -v -keystore mykeystorepath.keystore` to get it.

