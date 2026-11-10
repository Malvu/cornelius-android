# Installe les outils Android nécessaires pour compiler l'application Cornelius.
# Dossier cible : D:\Android\sdk  (hors du dossier de travail, réutilisable ensuite)
$ErrorActionPreference = 'Stop'
$sdk = 'D:\Android\sdk'
$zip = 'D:\Android\cmdline-tools.zip'
$url = 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'

New-Item -ItemType Directory -Force -Path 'D:\Android' | Out-Null

if (-not (Test-Path $zip)) {
  Write-Output "Téléchargement des outils en ligne de commande Android..."
  curl.exe -L --fail --retry 3 -o $zip $url
} else {
  Write-Output "Archive déjà téléchargée."
}

if (-not (Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
  New-Item -ItemType Directory -Force -Path "$sdk\cmdline-tools" | Out-Null
  Write-Output "Décompression..."
  Expand-Archive -Path $zip -DestinationPath "$sdk\cmdline-tools\tmp" -Force
  if (Test-Path "$sdk\cmdline-tools\latest") { Remove-Item -Recurse -Force "$sdk\cmdline-tools\latest" }
  Move-Item "$sdk\cmdline-tools\tmp\cmdline-tools" "$sdk\cmdline-tools\latest"
  Remove-Item -Recurse -Force "$sdk\cmdline-tools\tmp"
}

$sdkmanager = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
Write-Output "Acceptation des licences..."
$yes = ("y`n" * 40)
$yes | & $sdkmanager --sdk_root="$sdk" --licenses | Out-Null

Write-Output "Installation des paquets..."
& $sdkmanager --sdk_root="$sdk" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

Write-Output "Vérification :"
& $sdkmanager --sdk_root="$sdk" --list_installed
