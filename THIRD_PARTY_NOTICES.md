# Third-Party Notices

OpenPage includes or depends on the following third-party software. Their licenses remain applicable to those components and are not replaced by the MIT License for OpenPage's original code.

## Inter typeface

The Inter font files in `app/src/main/res/font/` are from The Inter Project Authors and are licensed under the SIL Open Font License, Version 1.1. The complete license text is preserved in `app/src/main/assets/inter_ofl_license.txt`.

Copyright (c) 2016 The Inter Project Authors: https://github.com/rsms/inter

## AndroidX, Jetpack Compose, Kotlin, and Gradle components

The AndroidX libraries, Jetpack Compose libraries, Kotlin tooling, and Gradle tooling referenced by the build are third-party dependencies. Their applicable license notices are distributed by their respective projects and artifacts. The primary licenses used by these ecosystems are Apache License 2.0 and the Kotlin License; users should consult the resolved dependency metadata for the exact version and notice applicable to a particular binary distribution.

OpenPage does not claim ownership of these dependencies. The dependency versions are declared in `gradle/libs.versions.toml` and `app/build.gradle.kts`.

## Printer and scanner protocol documentation

The driver implementations target publicly documented printer and scanner command languages and protocols. OpenPage does not include vendor SDKs or vendor firmware. Printer and scanner trademarks remain the property of their respective owners; references to brands and models describe compatibility targets and do not imply endorsement or certification.
