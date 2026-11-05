# Release signing

Release variants use a dedicated release keystore only when all four settings are provided; otherwise Gradle produces unsigned APKs. Debug keys are never used to sign release variants.

Set these values in the build process environment or as local Gradle properties that are not committed:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Then build both packages with `:mobile:assembleRelease :wear:assembleRelease`. Keep the keystore and credentials backed up securely and outside source control. An unsigned artifact is suitable for inspection but cannot be installed or published until signed. Existing installations can only be upgraded by an APK signed with the same certificate.

For Play Store bundles, use `:mobile:bundleRelease :wear:bundleRelease`, then verify both `.aab` files with `jarsigner -verify -verbose -certs`. Do not upload unless verification succeeds and the signer is the owner-authorized upload certificate. Record only the certificate SHA-256 and artifact SHA-256 in release evidence; never record passwords or the private keystore.

If this is the first Play listing, enroll in Play App Signing and use the owner-controlled upload key. If the package already exists in Play Console, follow the existing Play App Signing/upload-key recovery path; creating an unrelated key does not prove update continuity.
