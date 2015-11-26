define([ "./app_module" ], function(module) {
	"use strict";
	module.directive("appTile", function() {
		return {
			type : "E",
			templateUrl: "tile.html",
			replace: true,
			scope : {
				cell : "="
			}
		};
	});
});
