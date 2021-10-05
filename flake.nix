# This file allows building Briar with Nix build system.
# To learn more about nix, see: https://nixos.org/
# To enable flakes (required for building this project), see:
# https://nixos.wiki/wiki/Flakes
# To update dependencies (if this build fails), please run:
# > nix develop .
# > generate --root-dir ../. --jobs <Num of your cores>
# > exit
# Profit ! (aka it should build)
{
  description = "Briar is a messaging app designed for activists, journalists, and anyone else who needs a safe, easy and robust way to communicate.";

  inputs.android2nix.url = "github:Mazurel/android2nix";

  inputs.briar = {
    url = "git+https://code.briarproject.org/briar/briar.git";
    flake = false;
  };

  outputs = { self, android2nix, briar }:
    android2nix.lib.mkAndroid2nixEnv (
      { lib, stdenv, gradle_6, jdk11, fetchurl, ... }: rec {
      pname = "briar";

      src = let
        obfs4proxy-android = fetchurl {
          url = "https://plugins.gradle.org/m2/org/briarproject/obfs4proxy-android/0.0.12-dev-40245c4a/obfs4proxy-android-0.0.12-dev-40245c4a.zip";
          sha256 = "10pacrw95jin1aj9a3xxasky7n8638l5qribmfsjrglihf7mmc4a";
        };
      in stdenv.mkDerivation {
        inherit pname;
        version = "dev";
        src = briar;
        dontBuild = true;
        dontConfigure = true;

        installPhase = ''
          mkdir -p $out
          cp -rf ./* $out/
        '';

        fixupPhase = ''
          cd $out
          
          # Disable Gradle witness
          find . -name "build.gradle" -exec sed -i "s/classpath files('libs\/gradle-witness\.jar')//" {} \;
          find . -name "build.gradle" -exec sed -i "s/apply \(plugin\|from\): 'witness\(\.gradle\)\?'//" {} \;
          find . -name "build.gradle" -exec sed -i "s/id 'witness'//" {} \;

          # Use prefetched obfs4proxy-android zip as it doesn't work when fetched by gradle, for some reason 
          sed -i "s|'org.briarproject:obfs4proxy-android:0.0.12-dev-40245c4a@zip'|files('${obfs4proxy-android}')|" ./bramble-android/build.gradle
        '';
      };

      gradlePkg = gradle_6;
      jdk = jdk11;
      
      devshell = ./nix/devshell.toml;
      deps = ./nix/deps.json;
      buildType = "assembleOfficial";
    });
}
