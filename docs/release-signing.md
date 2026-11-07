# Release signing

Release variants use a dedicated release keystore only when all four settings are provided; otherwise Gradle produces unsigned APKs. Debug keys are never used to sign release variants.

Set these values in the build process environment or as local Gradle properties that are not committed:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Then build both packages with `:mobile:assembleRelease :wear:assembleRelease`. Keep the keystore and credentials backed up securely and outside source control. An unsigned artifact is suitable for inspection but cannot be installed or published until signed. Existing installations can only be upgraded by an APK signed with the same certificate.

For Play Store bundles, use `:mobile:bundleRelease :wear:bundleRelease`, then verify both `.aab` files with `jarsigner -verify -verbose -certs`. Do not upload unless verification succeeds and the signer is the owner-authorized upload certificate. Record only the certificate SHA-256 and artifact SHA-256 in release evidence; never record passwords or the private keystore.

For a phone + Wear OS Play pair, pass **both** `PLAY_MOBILE_VERSION_CODE` and `PLAY_WEAR_VERSION_CODE` as distinct integer Gradle properties greater than the public test code 155. Example for build verification only:

```powershell
.\gradlew.bat :mobile:assembleRelease :wear:assembleRelease :mobile:bundleRelease :wear:bundleRelease -PPLAY_MOBILE_VERSION_CODE=156 -PPLAY_WEAR_VERSION_CODE=157
```

The default debug/test builds remain at code 155 when these properties are absent. The example codes are not reserved in Play; inspect Console version history before assigning final upload codes. The same properties affect all variants in that Gradle invocation, so use them only for the intended Play build. The build scripts reject a missing companion code, non-integer values, duplicates, or values at/below 155. Verify the resulting APK/AAB manifest version codes independently before upload.

If this is the first Play listing, enroll in Play App Signing and use the owner-controlled upload key. If the package already exists in Play Console, follow the existing Play App Signing/upload-key recovery path; creating an unrelated key does not prove update continuity.

A candidate upload key was generated outside Git on 2026-11-07. The two password files are encrypted with Windows DPAPI for the current user profile; they are **not portable recovery copies**. Before enrolling this key in Play, preserve the JKS and both passwords in a secure backup that the owner can restore independently of this PC. The repository helper `scripts/build-play-signed.ps1` reads the local DPAPI files and builds both AABs without exposing passwords in Gradle arguments. Example:

```powershell
.\scripts\build-play-signed.ps1 -SigningDirectory 'C:\Users\jay\Documents\HealthBridge-Play-Upload-Key' -MobileVersionCode 156 -WearVersionCode 157
```

The released debug APKs are signed by a different Android Debug certificate. If Play Console asks for proof of existing off-Play package ownership, follow the Console's exact challenge with that existing certificate; a new upload key cannot prove ownership of the debug signature. Never publish the JKS or password files.
