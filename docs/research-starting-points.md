# Research starting points

This list is deliberately not a specification. It is the starting material for independent verification.

## Official / product behavior

- Engine DJ support: https://support.enginedj.com/
- Engine DJ downloads and release notes: https://enginedj.com/downloads
- Denon DJ Prime GO: https://www.denondj.com/prime-go/

## Reverse engineering

- Denon Engine networking: https://deathcamel58.github.io/denon-reverse-engineering/engine-networking.html
- Denon Engine gRPC reference: https://deathcamel58.github.io/denon-reverse-engineering/engine-grpc.html
- Denon/Engine reverse-engineering project: https://github.com/deathcamel58/denon-reverse-engineering

## Open-source protocol implementations

- StageLinq: https://github.com/chrisle/StageLinq
- go-stagelinq: https://github.com/icedream/go-stagelinq
- DenonDJ EAAS Server: https://github.com/andyscuff/DenonDJ-Eaas-Server

## Android platform

- ConnectivityManager: https://developer.android.com/reference/android/net/ConnectivityManager
- ConnectivityManager.NetworkCallback: https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
- Network: https://developer.android.com/reference/android/net/Network
- Android 14 foreground-service requirements: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Android 15 behavior changes: https://developer.android.com/about/versions/15/behavior-changes-15
- Android media/storage documentation: https://developer.android.com/training/data-storage

## gRPC

- gRPC Java: https://github.com/grpc/grpc-java

## Research rule

Every source above is evidence with a particular scope. The agent must record the device/version to which each statement applies and must not silently generalize from one Engine OS device or firmware version to another.
