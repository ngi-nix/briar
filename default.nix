{
  nixpkgsSource ? (builtins.fetchTarball {
    name = "nixpkgs-21.05";
    url = "https://github.com/NixOS/nixpkgs/archive/21.05.tar.gz";
    sha256 = "1ckzhh24mgz6jd1xhfgx0i9mijk6xjqxwsshnvq789xsavrmsc36";
  }),
  pkgs ? import nixpkgsSource {},
  pkgs_i686 ? import nixpkgsSource { system = "i686-linux"; },

  config ? pkgs.config
}:

let
  # Declaration of versions for everything. This is useful since these
  # versions may be used in multiple places in this Nix expression.
  android = {
    versions = {
      tools = "26.1.1";
      platformTools = "31.0.2";
      buildTools = "28.0.3";
      ndk = [
        "21.3.6528147" # LTS NDK
      ];
      cmake = "3.18.1";
      emulator = "30.6.3";
    };

    platforms = ["23" "24" "25" "26" "27" "28" "29" "30"];
    abis = ["armeabi-v7a" "arm64-v8a"];
    extras = ["extras;google;gcm"];
  };

  # If you copy this example out of nixpkgs, something like this will work:
  androidEnvNixpkgs = fetchTarball {
    name = "androidenv";
    url = "https://github.com/NixOS/nixpkgs/archive/refs/tags/21.05.tar.gz";
    sha256 = "sha256:1ckzhh24mgz6jd1xhfgx0i9mijk6xjqxwsshnvq789xsavrmsc36";
  };

  androidEnv = pkgs.callPackage "${androidEnvNixpkgs}/pkgs/development/mobile/androidenv" {
    inherit config pkgs pkgs_i686;
    licenseAccepted = true;
  };


  androidComposition = androidEnv.composeAndroidPackages {
    toolsVersion = android.versions.tools;
    platformToolsVersion = android.versions.platformTools;
    buildToolsVersions = [android.versions.buildTools];
    platformVersions = android.platforms;
    abiVersions = android.abis;

    includeSources = true;
    includeSystemImages = true;
    includeEmulator = true;
    emulatorVersion = android.versions.emulator;

    includeNDK = true;
    ndkVersions = android.versions.ndk;
    cmakeVersions = [android.versions.cmake];

    useGoogleAPIs = true;
    includeExtras = android.extras;

    # If you want to use a custom repo JSON:
    # repoJson = ../repo.json;

    # If you want to use custom repo XMLs:
    /*repoXmls = {
      packages = [ ../xml/repository2-1.xml ];
      images = [
        ../xml/android-sys-img2-1.xml
        ../xml/android-tv-sys-img2-1.xml
        ../xml/android-wear-sys-img2-1.xml
        ../xml/android-wear-cn-sys-img2-1.xml
        ../xml/google_apis-sys-img2-1.xml
        ../xml/google_apis_playstore-sys-img2-1.xml
      ];
      addons = [ ../xml/addon2-1.xml ];
    };*/

    # Accepting more licenses declaratively:
    extraLicenses = [
      # Already accepted for you with the global accept_license = true or
      # licenseAccepted = true on androidenv.
      # "android-sdk-license"

      # These aren't, but are useful for more uncommon setups.
      "android-sdk-preview-license"
      "android-googletv-license"
      "android-sdk-arm-dbt-license"
      "google-gdk-license"
      "intel-android-extra-license"
      "intel-android-sysimage-license"
      "mips-android-sysimage-license"
    ];
  };

  androidSdk = androidComposition.androidsdk;
  platformTools = androidComposition.platform-tools;
  jdk = pkgs.jdk11;
in
  with pkgs; pkgs.stdenv.mkDerivation rec {

  src = fetchFromGitHub {
    owner = "briar";
    repo = "briar";
    rev = "445ef0818cb80a4b7e4c1f4370293b0f869c2ece";
    sha256 = "sha256-rQ9EnRERqCDZdpX7ZfPEg11EhIHsVGEuDb0ip5RmnJs=";
  };


  name = "briar-mobile-app";
  buildInputs = [
    androidSdk
    platformTools
    pkgs.jdk11
    gradle_5
  ];
   
  dontPatchShebangs = false;

  
  buildPhase=''
    patchShebangs gradlew
    export LANG="C.UTF-8";
    export LC_ALL="C.UTF-8";
    export JAVA_HOME=${jdk11.home};

    # Note: ANDROID_HOME is deprecated. Use ANDROID_SDK_ROOT.
    export ANDROID_SDK_ROOT="${androidSdk}/libexec/android-sdk";
    export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk-bundle";

    export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_SDK_ROOT/build-tools/${android.versions.buildTools}/aapt2";

    cmake_root="$(echo "$ANDROID_SDK_ROOT/cmake/${android.versions.cmake}"*/)"
    export PATH="$cmake_root/bin:$PATH"
    pwd
    ls -l
    ./gradlew assembleRelese
'';
}

