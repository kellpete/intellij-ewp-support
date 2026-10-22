## Coding

- Do not use deprecated, obsolete or internal intellij-platform classes or methods
- Update changelog.md for major features and bugfixes

## Builds and tests (Windows)

Gradle downloads of the IntelliJ Platform / CLion SDK often fail with `PKIX path building failed` /
`certificate_unknown` unless the JVM uses the Windows trust store. Do **not** put this in the project
`gradle.properties` (it breaks CI on GitHub). Prefer one of:

- User Gradle props: `C:\Users\<username>\.gradle\gradle.properties` with  
  `org.gradle.jvmargs=-Djavax.net.ssl.trustStoreType=Windows-ROOT`
- One-off: `$env:GRADLE_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"` before `./gradlew …`

If `JAVA_HOME` is unset or invalid in the agent shell, use a JDK under `%USERPROFILE%\.jdks\` (e.g. `jbr-21*`).
