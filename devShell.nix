{ mkShell, buildFHSUserEnv, callPackage, briar, gradle2nix, jdk11, androidComposition, fetchgit, runtimeShell, ... }:
let
  mkShellFunctions = callPackage ./mkShellFunctions.nix {};

  sdk-env = buildFHSUserEnv {
    name = "sdk-env";
    targetPkgs = pkgs: (with pkgs;
    [
      gradle2nix
      androidComposition.androidsdk
      glibc
      jdk11
    ]);
  };
in
mkShell rec {
  name = "shell";

  GRADLE_OPTS =
    "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidComposition.androidsdk}/libexec/android-sdk/build-tools/30.0.3/aapt2";
  
  ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";
  ANDROID_NDK_ROOT = "${ANDROID_SDK_ROOT}/ndk-bundle";
  ANDROID_JAVA_HOME = "${jdk11.home}";
  JAVA_HOME = "${jdk11.home}";

  packages = [
    gradle2nix
    androidComposition.platform-tools
    jdk11
  ];

  shellHook = mkShellFunctions {
    load-source = ''
      [ -d src ] && rm -rfd src

      mkdir -p src
      cp -rfd ${briar}/* src/

      chmod -R +rw src
    '';
    
    gradle2nix-make = ''
      [ ! -d src ] && load-source
      cd src

      gradle2nix -nb -p :briar-android -c build . 
      
      cd ..
    '';

    sdk-env = ''
    ${sdk-env}/bin/sdk-env bash 
    '';
    
    impure-build = ''
      [ ! -d src ] && load-source
      
      cd src
      ${sdk-env}/bin/sdk-env ./gradlew build
     
      cd ..
 
      echo Avaible apks:
      find src -name "*.apk"
    '';
  };
}
