define([ "./app_module", "lodash" ], function(module, _) {
	"use strict";
	module.controller("Grid", ["$scope", function($scope) {
		var addrRE = /akka\..*:\/\/.*@(.+):.*/;
		function ip(address) {
			return addrRE.exec(address)[1];
		}
		var cells = [];
		this.cells = cells;

		var connectedServerIp;

		$scope.$on("Connected", function() {
			cells.splice(0, cells.length);
			connectedServerIp = undefined;
		});

		$scope.$on("WelcomeMessage", function(event, data) {
				connectedServerIp = ip(data.address);
		});

		$scope.$on("MemberUp", function(event, data) {
			var memberIp = ip(data.member.uniqueAddress.address);
			if(_.find(cells, { ip : memberIp }) === undefined) {
				cells.push({
					address : data.member.uniqueAddress.address,
					ip : memberIp,
					leader : false,
					roles : data.member.roles,
					roleLeader : {},
					status : data.member.status,
					reachable : true,
					isConnected : function() {
						return memberIp === connectedServerIp;
					}
				});
				$scope.$digest();
			}
		});

		$scope.$on("MemberRemoved", function(event, data) {
			var memberIp = ip(data.member.uniqueAddress.address);
			var idx = _.findIndex(cells, { ip : memberIp });
			if(idx > 0) {
				cells.splice(idx, 1);
				$scope.$digest();
			}
		});

		$scope.$on("LeaderChanged", function(event, data) {
			var newLeaderIp = data.leader ? ip(data.leader) : undefined;
			_.forEach(cells, function(cell) {
				if(cell.ip === newLeaderIp) {
					cell.leader = true;
				} else {
					cell.leader = false;
				}
			});
			$scope.$digest();
		});

		$scope.$on("RoleLeaderChanged", function(event, data) {
			var newLeaderIp = data.leader ? ip(data.leader) : undefined;
			_.forEach(cells, function(cell) {
				if(cell.ip === newLeaderIp) {
					cell.roleLeader[data.role] = true;
				} else {
					cell.roleLeader[data.role] = false;
				}
			});
			$scope.$digest();
		});

		$scope.$on("UnreachableMember", function(event, data) {
			var memberIp = ip(data.member.uniqueAddress.address);
			var member = _.find(cells, { ip : memberIp });
			if(member) {
				member.reachable = false;
			}
			$scope.$digest();
		});

		$scope.$on("ReachableMember", function(event, data) {
			var memberIp = ip(data.member.uniqueAddress.address);
			var member = _.find(cells, { ip : memberIp });
			if(member) {
				member.reachable = true;
			}
			$scope.$digest();
		});
	}]);
});
