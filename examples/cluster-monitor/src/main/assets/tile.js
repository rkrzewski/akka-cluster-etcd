define([ "./app_module" ], function(module) {
	"use strict";
	module.directive("appTile", ["connector", function(connector) {
		function link(scope) {
			scope.shutdown = function(target, immediate) {
				connector.send({
					target : target,
					immediate : immediate
				});
			};
		}

		return {
			type : "E",
			templateUrl: "tile.html",
			replace: true,
			scope : {
				cell : "="
			},
			link : link
		};
	}]);
});
