{
  description = "virtual environments";

  inputs.android2nix.url = "github:Mazurel/android2nix";

  outputs = { self, android2nix }:
    android2nix.lib.mkAndroid2nixEnv rec {
      pname = "briar";

      mkSrc = { stdenv, ... }: stdenv.mkDerivation {
        pname = "briar";
        version = "dev";
        src = ./.;
        dontBuild = true;
        dontConfigure = true;

        installPhase = ''
          mkdir -p $out
          cp -rf ./* $out/
        '';

        fixupPhase = ''
          cd $out

          find . -name "build.gradle" -exec sed -i "s/classpath files('libs\/gradle-witness\.jar')//" {} \;
          find . -name "build.gradle" -exec sed -i "s/apply \(plugin\|from\): 'witness\(\.gradle\)\?'//" {} \;
          find . -name "build.gradle" -exec sed -i "s/id 'witness'//" {} \;
          # find . -name "build.gradle" -exec sed -i "s/tor 'org.briarproject:obfs4proxy-android:0.0.12-dev-40245c4a@zip'//" {} \;
        '';
      };

      devshell = ./nix/devshell.toml;
      deps = ./nix/deps.json;
      buildType = "assembleOfficial";
    };
}
