# Building
Building is fairly simple as we are using maven, though, if you would like to speed up the process of iterative changes, you can setup a build script without stepping on any other developers very simply.

## 1. Edit the pom.xml
Under profiles, add your own profile and reference a script with your username
```xml
<profile>
  <id>cyberpwn</id>
  <properties>
    <bile.script>cyberpwn.bat</bile.script>
  </properties>
</profile>
```

## 2. Create your script
The scrip should basically copy the output jar to your test server locally. Pair this with bile tools to keep things snappy!
```batch
echo F|xcopy /y /s /f /q "%1" "C:\Users\cyberpwn\Documents\development\servers\dynamic\plugins\React.jar"
```
%1 is the built jar in the target folder. Simply change the second parameter to wherever you want it.
