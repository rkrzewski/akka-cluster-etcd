define([ "./tiles_module" ], function(module) {
	"use strict";
	module.directive("appTile", function() {
		function link(scope, element) {
		}
		return {
			type : "E",
			templateUrl: "tiles/tile.html",
			replace: true,
			link : link
		};
	});
});