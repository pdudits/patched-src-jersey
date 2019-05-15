Recommended command for building patch version

````
mvn clean deploy -Dtests.excluded -Dexamples.excluded
````

When changing versions, don't forget to additionally change version of `bom/pom.xml`
as `mvn versions:set` does not propagate to it. Or use `-DprocessAllModules`