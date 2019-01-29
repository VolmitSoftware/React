# Building
Building is fairly simple as we are using maven, though, if you would like to speed up the process of iterative changes, you can setup a build script without stepping on any other developers very simply.

## Edit the pom.xml
Under profiles, add your own profile and reference a script with your username
```xml
<profile>
  <id>cyberpwn</id>
  <properties>
    <react.development.location>${user.home}\Documents\development\servers\dynamic\plugins\React.jar</react.development.location>
  </properties>
</profile>
```

Then Build with your profile! You're done!
