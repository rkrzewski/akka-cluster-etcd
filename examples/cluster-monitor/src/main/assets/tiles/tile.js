define([ "./tiles_module" ], function(module) {
	"use strict";
	module.directive("appTile", function() {
		return {
			type : "E",
			templateUrl: "tiles/tile.html",
			replace: true,
			scope : {
				cell : "="				
			}
		};
	});
});