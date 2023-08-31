# React

The master branch is for the latest version of minecraft.

# [Support](https://discord.gg/3xxPTpT) **|** [Documentation](https://docs.volmit.com/react/)

# Building

Building React is fairly simple, though you will need to setup a few things if your system has never been used for java
development.

Consider supporting our development by buying React on spigot! We work hard to make React the best it can be for everyone.

## Preface: if you need help compiling and you are a developer / intend to help out in the community or with development we would love to help you regardless in the discord! however do not come to the discord asking for free copies, or a tutorial on how to compile.

### Command Line Builds

1. Install [Java JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
2. Set the JDK installation path to `JAVA_HOME` as an environment variable.
    * Windows
        1. Start > Type `env` and press Enter
        2. Advanced > Environment Variables
        3. Under System Variables, click `New...`
        4. Variable Name: `JAVA_HOME`
        5. Variable Value: `C:\Program Files\Java\jdk-17.0.1` (verify this exists after installing java don't just copy
           the example text)
    * MacOS
        1. Run `/usr/libexec/java_home -V` and look for Java 17
        2. Run `sudo nano ~/.zshenv`
        3. Add `export JAVA_HOME=$(/usr/libexec/java_home)` as a new line
        4. Use `CTRL + X`, then Press `Y`, Then `ENTER`
        5. Quit & Reopen Terminal and verify with `echo $JAVA_HOME`. It should print a directory
3. If this is your first time building React for MC 1.18+ run `gradlew setup` inside the root React project folder.
   Otherwise, skip this step. Grab a coffee, this may take up to 5 minutes depending on your cpu & internet connection.
4. Once the project has setup, run `gradlew React`
5. The React jar will be placed in `React/build/React-XXX-XXX.jar` Enjoy! Consider supporting us by buying it on spigot!

### IDE Builds (for development)

* Run `gradlew setup` any time you get dependency issues with craftbukkit
* Configure ITJ Gradle to use JDK 17 (in settings, search for gradle)
* Add a build line in the build.gradle for your own build task to directly compile React into your plugins folder if you
  prefer.
* Resync the project & run your newly created task (under the development folder in gradle tasks!)
