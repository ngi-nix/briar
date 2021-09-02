{ callPackage, briar, jdk11, androidComposition, gradle_6,  ... }:
let
  buildGradle = callPackage ./gradle-env.nix { };
  
in buildGradle rec {
  GRADLE_OPTS =
    "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidComposition.androidsdk}/libexec/android-sdk/build-tools/30.0.3/aapt2";
  
  ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";
  ANDROID_NDK_ROOT = "${ANDROID_SDK_ROOT}/ndk-bundle";
  ANDROID_JAVA_HOME = "${jdk11.home}";
  JAVA_HOME = "${jdk11.home}";

  # gradlePackage = gradle_6;
  buildJdk = jdk11;
  
  envSpec = ./gradle-env.json;
  src = briar;
  gradleFlags = [ ":briar-android:assemebleRelease" ];
  installPhase = ''
  mkdir -p $out

  # find src -name "*.apk" -exec cp {} $out/ \;
  cp -rf ./* $out
  '';
}
