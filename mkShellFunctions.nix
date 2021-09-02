{ lib, ... }:
# Makes all atributes inside shell functions
# and list them at the end so that user can see
# what is available.
set:
(
    lib.lists.foldl
    (a: b: "${a}${b}") ""
    (
	lib.attrsets.mapAttrsToList (
	name: value:
	    ''
	    function ${name}() {
	    ${value}
	    }
	    ''
	) set
    )
)
+ (
    lib.lists.foldl
    (
	a: b: ''
	${a}
	echo "- ${b}"
	''
    ) "echo Available Briar shell functions:\n"
    (builtins.attrNames set)
)
