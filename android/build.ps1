# Bouwt de Android-app (zonder Gradle): aapt2 -> javac -> d8 -> zipalign -> apksigner.
# De interface is dezelfde index.html als op de laptop.
# Uitvoer: android\out\Didgeridoo-<versie>.apk   Gebruik: .\build.ps1 [-Install]
param([switch]$Install)
# 'Continue': hulpprogramma's schrijven meldingen naar stderr; fouten worden via de exitcode (Check) afgevangen
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$java = 'C:\Program Files\Android\Android Studio\jbr\bin'
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt = "$sdk\build-tools\36.0.0"
$jar = "$sdk\platforms\android-36\android.jar"
$build = "$root\build"
$out = "$root\out"
$version = ([xml](Get-Content "$root\AndroidManifest.xml")).manifest.versionName
function Check($step) { if ($LASTEXITCODE -ne 0) { throw "Mislukt: $step" } }

Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$build\classes", "$build\dex", "$build\gen", "$build\assets", $out | Out-Null
Copy-Item "$root\..\index.html", "$root\..\logo.png" "$build\assets"

& "$bt\aapt2.exe" compile --dir "$root\res" -o "$build\res.zip"; Check 'resources compileren'
& "$bt\aapt2.exe" link -o "$build\unsigned.apk" -I $jar --manifest "$root\AndroidManifest.xml" -A "$build\assets" --java "$build\gen" "$build\res.zip"; Check 'resources linken'

$sources = @(Get-ChildItem "$root\src", "$build\gen" -Recurse -Filter *.java | ForEach-Object FullName)
& "$java\javac.exe" -encoding UTF-8 --release 8 -nowarn -Xlint:-options -classpath $jar -d "$build\classes" @sources; Check 'Java compileren'
$classes = @(Get-ChildItem "$build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
& "$java\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --min-api 26 --lib $jar --output "$build\dex" @classes; Check 'dex'

Copy-Item "$build\unsigned.apk" "$build\with-dex.apk"
Push-Location "$build\dex"
try { & "$java\jar.exe" uf "$build\with-dex.apk" classes.dex; Check 'dex toevoegen' } finally { Pop-Location }
& "$bt\zipalign.exe" -f 4 "$build\with-dex.apk" "$build\aligned.apk"; Check 'zipalign'

# Eigen ondertekeningssleutel; bewaar deze map: updates moeten met dezelfde sleutel ondertekend zijn
$key = "$root\data\release.keystore"
if (!(Test-Path $key)) {
  New-Item -ItemType Directory -Force "$root\data" | Out-Null
  & "$java\keytool.exe" -genkeypair -keystore $key -storepass didgeridoo -alias didgeridoo -keypass didgeridoo -dname 'CN=Didgeridoo' -keyalg RSA -keysize 2048 -validity 10000; Check 'sleutel maken'
}
$apk = "$out\Didgeridoo-$version.apk"
& "$java\java.exe" -jar "$bt\lib\apksigner.jar" sign --ks $key --ks-key-alias didgeridoo --ks-pass pass:didgeridoo --key-pass pass:didgeridoo --out $apk "$build\aligned.apk"; Check 'ondertekenen'
& "$java\java.exe" -jar "$bt\lib\apksigner.jar" verify $apk; Check 'handtekening controleren'
Write-Host "Klaar: $apk"

if ($Install) {
  & adb install -r $apk; Check 'installeren'
}
