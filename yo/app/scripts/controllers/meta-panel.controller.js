

(function() {
    'use strict';

    var app = angular.module('topcat');

    app.controller('MetaPanelController', function($scope, $translate, $q, tc, helpers, icatSchema){
        var that = this;
        var previousEntityHash;
        var timeout = $q.defer();
        $scope.$on('$destroy', function(){ timeout.resolve(); });

        this.getSize = function(entity){
            entity.getSize(timeout.promise);
        };

        $scope.$on('rowclick', function(event, entity){

            var facility = tc.facility(entity.facilityName);
            var config;
            if(entity.type == 'facility'){
                config = tc.config().browse.metaTabs;
            } else if(facility.config().browse[entity.type]) {
                config = facility.config().browse[entity.type].metaTabs;
            }

            var entityHash = entity.facilityName + ":" + entity.type + ":" + entity.id;
            if(entityHash == previousEntityHash){
                that.tabs = [];
                previousEntityHash = undefined;
                return;
            }
            previousEntityHash = entityHash;
            
            if(!config) {
                // Build a single tab that reports "No metadata available"
                var tab = {
                    title: $translate.instant("METATABS.NO_METADATA.TABTITLE"),
                    items: [{
                        label: "",
                        value: $translate.instant("METATABS.NO_METADATA.VALUE")
                    }]
                }
                that.tabs = [tab]
                return;
            };

            console.log("MetaPanel: entity type: " + entity.type);
            
            // Initialise the query for the metatab fields
            var queryBuilder = facility.icat().queryBuilder(entity.type).where(entity.type + ".id = " + entity.id);
            var entityType = entity.type;
            _.each(config, function(metaTab){
                _.each(metaTab.items, function(item){
                    var field = item.field;
                    if(!field) return;
                    var matches;
                    // Test for compound field expressions, e.g. "investigationUser.role"
                    if(matches = field.replace(/\|.+$/, '').match(/^([^\[\]]+).*?\.([^\.\[\]]+)$/)){
                    	// For a compound expression, we need to extract the corresponding entity type (e.g. "investigationUser" maps to "user")
                        var variableName = matches[1];
                        entityType = icatSchema.variableEntityTypes[variableName];
                        console.log("MetaPanel: var: " + variableName + "; type: " + entityType);
                        if(!entityType){
                            console.error("Unknown variableName: " + variableName, item)
                        }
                        // Add this entity type to the includes list
                        queryBuilder.include(entityType);
                        // (Re)set the field to the second value of the compound expression (e.g. "role")
                        field = matches[2];
                    } else {
                    	// Fixes issue #407 : ensure translation label for unmatched fields is set to the current entity,
                    	// rather than possibly inherited from the previous metaTab item.
                    	entityType = entity.type;
                    }

                    // Size values are special - calculated lazily on request
                    if(field == 'size'){   
                        item.template = '<span><span ng-if="item.entity.isGettingSize && $root.requestCounter != 0" class="loading collapsed">&nbsp;</span>{{item.entity.size|bytes}}<button class="btn btn-default btn-xs" ng-click="meta.getSize(item.entity)" ng-if="!item.entity.isGettingSize && item.entity.size === undefined">Calculate</button></span>';
                    }

                    // If the config doesn't explicitly define the label, calculate its translation string from the type and field
                    if(!item.label && item.label !== ''){
                        var entityTypeNamespace = helpers.constantify(entityType);
                        var fieldNamespace = helpers.constantify(field);
                        item.label = "METATABS." + entityTypeNamespace + "." + fieldNamespace;
                    }
                });
            });

            // var queryBuilder = facility.icat().queryBuilder(entity.type).where(entity.type + ".id = " + entity.id);

            /*
             * TEST
             *
            if(entity.type == 'instrument'){
                queryBuilder.include('instrumentScientist');
            }

            if(entity.type == 'investigation'){
                queryBuilder.include('user');
                queryBuilder.include('investigationParameterType');
                queryBuilder.include('sample');
                queryBuilder.include('publication');
                queryBuilder.include('study');
                queryBuilder.include('investigationUser');
            }

            if(entity.type == 'dataset'){
                queryBuilder.include('datasetParameterType');
                queryBuilder.include('sample');
                queryBuilder.include('datasetType');
            }

            if(entity.type == 'datafile'){
                queryBuilder.include('datafileParameterType');
                queryBuilder.include('datafileFormat');
            }
             */

            queryBuilder.run(timeout.promise).then(function(entity){
                entity = entity[0];

                var tabs = [];
                _.each(config, function(tabConfig){
                    var tab = {
                        title: $translate.instant(tabConfig.title),
                        items: []
                    };
                    _.each(tabConfig.items, function(itemConfig){
                        var find = entity.entityType;
                        var field = itemConfig.field;
                        console.log("MP 1: find=" + find + ", field=" + field);
                        var matches;
                        if(matches = itemConfig.field.replace(/\|.+$/, '').match(/^(.*)?\.([^\.\[\]]+)$/)){
                            find = matches[1];
                            field = matches[2]
                            console.log("MP 2: find=" + find + ", field=" + field);
                        }
                        if(!find.match(/\]$/)) find = find + '[]';
                        console.log("MP 3: find=" + find);
                        _.each(entity.find(find), function(entity){
                            var value = entity.find(field)[0];
                            console.log("MP 4: value=" + value);
                            if(value !== undefined || field == 'size'){
                                tab.items.push({
                                    label: itemConfig.label ? $translate.instant(itemConfig.label) : null,
                                    template: itemConfig.template,
                                    value: value,
                                    entity: entity
                                });
                            }
                        });
                    });

                    tabs.push(tab);
                });

                that.tabs = tabs;
            });
        });

    });

})();

