{
  description = "Briar :)";

  # Nixpkgs / NixOS version to use.
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  inputs.briar.url = "git+https://code.briarproject.org/briar/briar.git";
  inputs.briar.flake = false;

  inputs.flake-utils.url = "github:numtide/flake-utils";

  inputs.gradle2nix-flake.url = "github:tadfisher/gradle2nix";

  outputs = { self, nixpkgs, briar, gradle2nix-flake, flake-utils }:
    flake-utils.lib.eachSystem [ "x86_64-linux" ] (system:
      let
        pkgs = import nixpkgs { inherit system; config.android_sdk.accept_license = true; };
        gradle2nix = gradle2nix-flake.outputs.defaultPackage.${system};

        androidComposition = pkgs.callPackage ./android.nix { };
      in
      {
        devShell = pkgs.callPackage ./devShell.nix { inherit briar gradle2nix androidComposition; };

        defaultPackage = pkgs.callPackage ./briar.nix { inherit briar androidComposition; };
      }
    );
}
