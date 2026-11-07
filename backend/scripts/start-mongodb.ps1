$ErrorActionPreference = 'Stop'

# Mantém o MongoDB de desenvolvimento dentro do projeto.
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$mongoRoot = Join-Path $projectRoot '.local\mongodb'
$version = '8.0.34'
$mongodPath = Join-Path $mongoRoot "mongodb-win32-x86_64-windows-$version\bin\mongod.exe"
$dataPath = Join-Path $mongoRoot 'data'
$logPath = Join-Path $mongoRoot 'mongod.log'

# Baixa a distribuição oficial e confere a integridade antes de extrair.
if (!(Test-Path -LiteralPath $mongodPath)) {
    New-Item -ItemType Directory -Force $mongoRoot | Out-Null
    $archivePath = Join-Path $mongoRoot "mongodb-$version.zip"
    Invoke-WebRequest "https://fastdl.mongodb.org/windows/mongodb-windows-x86_64-$version.zip" -OutFile $archivePath
    $expectedHash = '6b021a0864494e4decf278b36477a29eb36072432773e4e62c2f42c302630db0'
    if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $expectedHash) {
        throw 'O arquivo baixado não corresponde ao SHA256 oficial.'
    }
    Expand-Archive -LiteralPath $archivePath -DestinationPath $mongoRoot -Force
}

# Evita iniciar outra instância quando a porta já está ocupada.
$listener = Get-NetTCPConnection -LocalPort 27017 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Output 'A porta 27017 já está em uso. Confira a instância existente antes de iniciar outra.'
    return
}

New-Item -ItemType Directory -Force $dataPath | Out-Null

# Aceita conexões apenas deste computador, sem criar um serviço do Windows.
$arguments = "--dbpath `"$dataPath`" --logpath `"$logPath`" --logappend --bind_ip 127.0.0.1 --port 27017"
$mongoProcess = Start-Process -FilePath $mongodPath -ArgumentList $arguments -WindowStyle Hidden -PassThru
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $mongoProcess.Refresh()
    if ($mongoProcess.HasExited) {
        throw "MongoDB encerrou durante a inicialização. Consulte $logPath"
    }
    if (Get-NetTCPConnection -LocalPort 27017 -State Listen -ErrorAction SilentlyContinue) {
        Write-Output "MongoDB iniciado em 127.0.0.1:27017 (PID $($mongoProcess.Id))."
        return
    }
    Start-Sleep -Milliseconds 500
}
throw "MongoDB ainda não abriu a porta. Consulte $logPath"
