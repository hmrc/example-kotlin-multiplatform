# Example Kotlin Multiplatform Project


This project provide a basis for a Kotlin Multiplatform project. It supports generating a .Framework for use in iOS 32-bit and 64-bit devices. It will also generate a .jar that can be used on any JVM project, for example a Play/Spring project web services as-well as in Android.


## Steps to use this project


1. Clone project and import into IntelliJ as a Gradle project.
2. Open settings.gradle.kts and change `rootProject.name` to be the name of your desired project
3. Rename `credentials.properties.example` to `credentials.properties` 

    Input values for each variable. For example: 
    ```
    bintray.user=userID
    bintray.apikey=APIKey
    ```
    
    If you do not need to publish to Bintray just ensure the file exists

    > Write some code...

4. To build the artifacts for iOS and Android run the `build` gradle task. These artifacts are in the build folder
5. Before committing I would recommend running `cleanBuildTestCoverage` as this will do a full run of build, test and validation coverage results and updating the README with coverage badges.

