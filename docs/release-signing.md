# Release signing

Release variants use a dedicated release keystore only when all four settings are provided; otherwise Gradle produces unsigned APKs. Debug keys are never used to sign release variants.

Set these values in the build process environment or as local Gradle properties that are not committed:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Then build both packages with `:mobile:assembleRelease :wear:assembleRelease`. Keep the keystore and credentials backed up securely and outside source control. An unsigned artifact is suitable for inspection but cannot be installed or published until signed. Existing installations can only be upgraded by an APK signed with the same certificate.
