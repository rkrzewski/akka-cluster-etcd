define([ "./app_module" ], function(module) {
	module.service("connector", [ "$window", "$location", "$timeout", "$log", "$rootScope",
		function($window, $location, $timeout, $log, $rootScope) {
			var reconnectDelay = 15000;
			var active = false;
			var ws;
			function connect() {
				var protocol = $location.protocol().replace("http","ws");
				ws = new $window.WebSocket(protocol + "://" + $location.host() + ":" +
					$location.port() + "/ws");
				ws.onopen = function() {
					active = true;
					$log.info("WebSocket connected");
					$rootScope.$broadcast("Connected", {
						connected : true
					});
				};
				ws.onclose = function(evt) {
					active = false;
					$log.warn("WebSocket closed, code: " + evt.code +
						(evt.reason ? " reason: " + evt.reason : "") + " reconnecting in " +
						reconnectDelay / 1000 + "s");
					$rootScope.$broadcast("Connected", {
						connected : false
					});
					$timeout(connect, reconnectDelay);
				};
				ws.onmessage = function(evt) {
					var data = JSON.parse(evt.data);
					if (data.event) {
						$rootScope.$broadcast(data.event, data);
					} else {
						$log.error("invalid message, 'event' property missing");
					}
				};
			}
			function send(msg) {
				if(active) {
					ws.send(JSON.stringify(msg));
				}
			}
			return {
				connect : connect,
				send : send,
				active : function() {
					return active;
				}
			};
		}
	]);

	module.controller("ConnectionMonitor", ["$scope", function($scope) {
		$scope.connected = false;
		$scope.$on("Connected", function(event, data) {
			$scope.$apply(function(scope) {
				scope.connected = data.connected;
			});
		});
	}]);

	module.run(["connector", function(connector) {
		connector.connect();
	}]);
});
