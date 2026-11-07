# Configura o MongoDB local no terminal atual.
# Execute com um ponto e um espaço antes do caminho para usar neste terminal.
$env:MONGODB_URI = 'mongodb://127.0.0.1:27017'
$env:MONGODB_DATABASE = 'dosecerta_dev'

Write-Output 'Ambiente local configurado: MongoDB em 127.0.0.1:27017, banco dosecerta_dev.'
