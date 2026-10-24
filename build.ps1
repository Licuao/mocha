# Compila Mocha con el toolchain portable de D:\Android (sin instalar nada en el sistema).
$env:JAVA_HOME = "D:\Android\jdk17"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:GRADLE_USER_HOME = "D:\Android\gradle-home"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& "$PSScriptRoot\gradlew.bat" @args
exit $LASTEXITCODE
