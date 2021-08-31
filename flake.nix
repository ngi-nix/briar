{
  inputs = {
    nixpkgs.url = "nixpkgs/21.05";
    gradle2nix.url = "github:tadfisher/gradle2nix";
    briar-src = { url = "git+https://code.briarproject.org/briar/briar"; flake = false; };
  };

  outputs = { self, nixpkgs, gradle2nix, briar-src }:
    let
      # System types to support.
      supportedSystems = [ "x86_64-linux" ];
      # Helper function to generate an attrset '{ x86_64-linux = f "x86_64-linux"; ... }'.
      
      forAllSystems = f:
        nixpkgs.lib.genAttrs supportedSystems (system: f system);
      
      lib = nixpkgs.lib;

      # Nixpkgs instantiated for supported system types.
      nixpkgsFor = forAllSystems (system:
        import nixpkgs {
          inherit system;
          overlays = [ self.overlay overlayAndroid ];
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        });
      
      android = {
        versions = {
          tools = "26.1.1";
          platformTools = "31.0.2";
          buildTools = "30.0.3";
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
      androidEnvNixpkgs = nixpkgs;

      overlayAndroid = final: prev: 
        let
          pkgs = final;
          pkgs_i686 = import nixpkgs {
            system = "i686-linux";
            config.android_sdk.accept_license = true;
          };
        in
        rec {

          androidEnv = pkgs.callPackage "${androidEnvNixpkgs}/pkgs/development/mobile/androidenv" {
            inherit pkgs pkgs_i686;
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

        };

    in
    {

      # A Nixpkgs overlay.
      overlay = final: prev:
        let
          pkgs = final;
        in
        {
          briar-gradle2nix = (pkgs.callPackage ./gradle-env.nix {}) {
            envSpec = ./gradle-env.json;
    
            src = briar-src;
    
            gradleFlags = [ "assembleRelease" ];
    
            # installPhase = ''
            #   mkdir -p $out
            #   cp -r app/build/install/myproject $out
            # '';
          };

          briar = pkgs.stdenv.mkDerivation rec {

            name = "briar-mobile-app";

            src = briar-src;

            buildInputs = with pkgs; [
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
              export JAVA_HOME=${pkgs.jdk11.home};

              # Note: ANDROID_HOME is deprecated. Use ANDROID_SDK_ROOT.
              export ANDROID_SDK_ROOT="${pkgs.androidSdk}/libexec/android-sdk";
              export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk-bundle";

              export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_SDK_ROOT/build-tools/${android.versions.buildTools}/aapt2";

              cmake_root="$(echo "$ANDROID_SDK_ROOT/cmake/${android.versions.cmake}"*/)"
              export PATH="$cmake_root/bin:$PATH"
              pwd
              ls -l
              ./gradlew assembleRelese
            '';
          };

        };

      # Provide a nix-shell env to work with.
      devShell = forAllSystems (system:
        let
          pkgs = nixpkgsFor."${system}";
        in
        pkgs.mkShell rec {
          name = "androidenv-demo";
          buildInputs = with pkgs; [
          android-studio
            androidSdk
            platformTools
            pkgs.jdk11
            go
            flutter
            gomobile
            gradle
          ];
          LANG = "C.UTF-8";
          LC_ALL = "C.UTF-8";
          JAVA_HOME = pkgs.jdk.home;

          # Note: ANDROID_HOME is deprecated. Use ANDROID_SDK_ROOT.
          ANDROID_SDK_ROOT = "${pkgs.androidSdk}/libexec/android-sdk";
          ANDROID_NDK_ROOT = "${ANDROID_SDK_ROOT}/ndk-bundle";

          # Ensures that we don't have to use a FHS env by using the nix store's aapt2.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${ANDROID_SDK_ROOT}/build-tools/${android.versions.buildTools}/aapt2";

          shellHook = ''
            # Add cmake to the path.
            cmake_root="$(echo "$ANDROID_SDK_ROOT/cmake/${android.versions.cmake}"*/)"
            export PATH="$cmake_root/bin:$PATH"

            # Write out local.properties for Android Studio.
            cat <<EOF > local.properties
            # This file was automatically generated by nix-shell.
            sdk.dir=$ANDROID_SDK_ROOT
            ndk.dir=$ANDROID_NDK_ROOT
            cmake.dir=$cmake_root
            EOF
          '';
        }
      );

      # Provide some binary packages for selected system types.
      packages = forAllSystems (system: {
        inherit (nixpkgsFor.${system})
          briar
        ;
      });

      apps = forAllSystems (system: 
        let
          pkgs = nixpkgsFor."${system}";
        in
        {
          run-gradle2nix = {
            type = "app";
            program = builtins.toString (pkgs.writeScript "run-gradle2nix" ''
              PATH=${lib.makeBinPath (with pkgs; [
                coreutils
                gnused
                gradle2nix.outputs.defaultPackage."${system}"
              ])}
              set -e
              rm -rf src
              cp -r ${briar-src} src
              chmod -R +w src
              
              gradle2nix \
                -o . \
                -c assembleRelease \
                src
              rm -rf src
            '');
          };
        }
      );

      defaultPackage =
        forAllSystems (system: self.packages.${system}.briar);

    };
}
