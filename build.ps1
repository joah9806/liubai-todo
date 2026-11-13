param(
    [string]$SdkRoot = $env:ANDROID_SDK_ROOT,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$VersionName = "1.0",
    [int]$VersionCode = 1,
    [string]$KeystorePass = $env:TODO_KEYSTORE_PASS,
    [string]$KeyAlias = "todo"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
if (-not $SdkRoot) {
    $SdkRoot = Join-Path $env:LOCALAPPDATA "dsh-android-toolchain\sdk"
}
if (-not $JdkHome) {
    $JdkHome = Join-Path $env:LOCALAPPDATA "dsh-android-toolchain\jdk-17.0.2"
}

$BuildTools = Join-Path $SdkRoot "build-tools\35.0.0"
$AndroidJar = Join-Path $SdkRoot "platforms\android-35\android.jar"
$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$Zipalign = Join-Path $BuildTools "zipalign.exe"
$Apksigner = Join-Path $BuildTools "apksigner.bat"
$LambdaStubs = Join-Path $BuildTools "core-lambda-stubs.jar"
$Javac = Join-Path $JdkHome "bin\javac.exe"
$Keytool = Join-Path $JdkHome "bin\keytool.exe"

foreach ($tool in @($Aapt2, $D8, $Zipalign, $Apksigner, $LambdaStubs, $Javac, $Keytool, $AndroidJar)) {
    if (-not (Test-Path $tool)) {
        throw "Missing build dependency: $tool"
    }
}

$env:JAVA_HOME = $JdkHome
$env:Path = "$JdkHome\bin;$BuildTools;$env:Path"

$BuildDir = Join-Path $ProjectRoot "build"
$DistDir = Join-Path $ProjectRoot "dist"
$Manifest = Join-Path $ProjectRoot "app\src\main\AndroidManifest.xml"
$ResDir = Join-Path $ProjectRoot "app\src\main\res"
$JavaDir = Join-Path $ProjectRoot "app\src\main\java"
$Keystore = Join-Path $ProjectRoot "keystore\todo-release.jks"
$KeystorePassFile = Join-Path $ProjectRoot "keystore\keystore-password.txt"
if (-not $KeystorePass -and (Test-Path $KeystorePassFile)) {
    $KeystorePass = (Get-Content -Raw $KeystorePassFile).Trim()
}
if (-not $KeystorePass) {
    if (Test-Path $Keystore) {
        throw "A keystore exists but no password is available. Set TODO_KEYSTORE_PASS or create keystore/keystore-password.txt."
    }
    $randomBytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($randomBytes)
    $KeystorePass = [Convert]::ToBase64String($randomBytes)
    New-Item -ItemType Directory -Force -Path (Split-Path $Keystore) | Out-Null
    Set-Content -Encoding ASCII -NoNewline -Path $KeystorePassFile -Value $KeystorePass
    Write-Host "Generated a local signing password at $KeystorePassFile"
}
$FinalApk = Join-Path $DistDir "todo-$VersionName.apk"

if (Test-Path $BuildDir) {
    Remove-Item -Recurse -Force $BuildDir
}
New-Item -ItemType Directory -Force -Path $BuildDir, $DistDir, (Join-Path $ProjectRoot "keystore") | Out-Null

Write-Host "[1/7] Compiling resources"
$CompiledRes = Join-Path $BuildDir "resources.zip"
& $Aapt2 compile --dir $ResDir -o $CompiledRes
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/7] Linking resources"
$UnsignedApk = Join-Path $BuildDir "app-unsigned.apk"
$GenDir = Join-Path $BuildDir "gen"
New-Item -ItemType Directory -Force -Path $GenDir | Out-Null
& $Aapt2 link `
    -o $UnsignedApk `
    -I $AndroidJar `
    --manifest $Manifest `
    --java $GenDir `
    --min-sdk-version 26 `
    --target-sdk-version 35 `
    --version-code $VersionCode `
    --version-name $VersionName `
    $CompiledRes
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/7] Compiling Java"
$ClassesDir = Join-Path $BuildDir "classes"
New-Item -ItemType Directory -Force -Path $ClassesDir | Out-Null
$JavaFiles = @()
$JavaFiles += Get-ChildItem -Path $JavaDir -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$JavaFiles += Get-ChildItem -Path $GenDir -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$ArgFile = Join-Path $BuildDir "javac-sources.txt"
$JavaFiles | ForEach-Object { '"' + $_.Replace('\', '/') + '"' } |
    Set-Content -Encoding ASCII $ArgFile
& $Javac `
    -encoding UTF-8 `
    -source 8 `
    -target 8 `
    -Xlint:-options `
    -Xlint:-deprecation `
    -bootclasspath "$AndroidJar;$LambdaStubs" `
    -d $ClassesDir `
    "@$ArgFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/7] Dexing"
$DexDir = Join-Path $BuildDir "dex"
New-Item -ItemType Directory -Force -Path $DexDir | Out-Null
$ClassFiles = Get-ChildItem -Path $ClassesDir -Recurse -Filter *.class |
    Select-Object -ExpandProperty FullName
& $D8 --lib $AndroidJar --min-api 26 --release --output $DexDir @ClassFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/7] Packaging classes.dex"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Zip = [System.IO.Compression.ZipFile]::Open($UnsignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $Existing = $Zip.GetEntry("classes.dex")
    if ($Existing) { $Existing.Delete() }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $Zip,
        (Join-Path $DexDir "classes.dex"),
        "classes.dex",
        [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally {
    $Zip.Dispose()
}

$AlignedApk = Join-Path $BuildDir "app-aligned.apk"
& $Zipalign -p -f 4 $UnsignedApk $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[6/7] Signing"
if (-not (Test-Path $Keystore)) {
    & $Keytool -genkeypair -v `
        -keystore $Keystore `
        -storepass $KeystorePass `
        -keypass $KeystorePass `
        -alias $KeyAlias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 10000 `
        -dname "CN=Liubai Todo, OU=Mobile, O=Liubai, L=Shanghai, ST=Shanghai, C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}
& $Apksigner sign `
    --ks $Keystore `
    --ks-key-alias $KeyAlias `
    --ks-pass "pass:$KeystorePass" `
    --key-pass "pass:$KeystorePass" `
    --out $FinalApk `
    $AlignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

Write-Host "[7/7] Verifying"
& $Apksigner verify --verbose --print-certs $FinalApk
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }
& $Aapt2 dump badging $FinalApk
if ($LASTEXITCODE -ne 0) { throw "aapt2 dump badging failed" }

$Size = [math]::Round((Get-Item $FinalApk).Length / 1KB, 1)
$Hash = (Get-FileHash -Algorithm SHA256 $FinalApk).Hash.ToLowerInvariant()
$HashFile = Join-Path $DistDir "todo-$VersionName.apk.sha256"
"$Hash  todo-$VersionName.apk" | Set-Content -Encoding ASCII $HashFile
Write-Host ""
Write-Host "SHA-256: $Hash"
Write-Host "APK ready: $FinalApk ($Size KiB)"
