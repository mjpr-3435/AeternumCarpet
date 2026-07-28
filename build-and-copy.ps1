.\gradlew.bat build
if ($?) {
    $jar = Get-ChildItem -Path "build\libs\*.jar" | Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*dev*" } | Select-Object -First 1
    $dest = "C:\Users\Mauricio\AppData\Roaming\ModrinthApp\profiles\Minecraft 1.21.6\mods"
    if ($jar) {
        Copy-Item -LiteralPath $jar.FullName -Destination $dest -Force
        Write-Host "Copiado: $($jar.Name)"
    } else {
        Write-Host "No se encontró el jar compilado."
    }
}
