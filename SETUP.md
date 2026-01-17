🚀 Guia de Configuração - Transcription API
📋 Pré-requisitos
Obrigatórios

☑️ Java 17+ - Download
☑️ MySQL 8.0+ - Download
☑️ FFmpeg - Download
☑️ OpenAI API Key - Obter aqui

Opcionais

Maven 3.9+ (ou use o wrapper ./mvnw)
Git


🔧 Instalação dos Pré-requisitos
1. Instalar Java 17
# Verificar se Java está instalado
   java -version

# Linux (Ubuntu/Debian)
sudo apt update
sudo apt install openjdk-17-jdk

# MacOS (Homebrew)
brew install openjdk@17

# Windows
# Download: https://adoptium.net/
2. Instalar MySQL
# Linux (Ubuntu/Debian)
   sudo apt update
   sudo apt install mysql-server
   sudo systemctl start mysql
   sudo mysql_secure_installation

# MacOS (Homebrew)
brew install mysql
brew services start mysql

# Windows
# Download: https://dev.mysql.com/downloads/installer/
Configurar MySQL:
-- Criar usuário (opcional)
CREATE USER 'transcription_user'@'localhost' IDENTIFIED BY 'sua-senha';
GRANT ALL PRIVILEGES ON transcription_db.* TO 'transcription_user'@'localhost';
FLUSH PRIVILEGES;

-- Criar database (opcional - será criado automaticamente)
CREATE DATABASE transcription_db;
3. Instalar FFmpeg
   Linux (Ubuntu/Debian)
   sudo apt update
   sudo apt install ffmpeg

# Verificar instalação
ffmpeg -version
MacOS (Homebrew)
brew install ffmpeg

# Verificar instalação
ffmpeg -version
Windows
Opção 1: Chocolatey (Recomendado)
choco install ffmpeg
Opção 2: Download Manual

Download: https://www.gyan.dev/ffmpeg/builds/
Extrair para C:\ffmpeg
Adicionar ao PATH:

Abrir "Variáveis de Ambiente"
Editar PATH do usuário
Adicionar: C:\ffmpeg\bin


Reiniciar terminal

Verificar:
ffmpeg -version

⚙️ Configuração da Aplicação
1. Clonar o Repositório
   git clone <seu-repositorio>
   cd transcription-api
2. Copiar Arquivo de Ambiente
   # Linux/Mac
   cp .env.example .env

# Windows
copy .env.example .env
3. Configurar Variáveis de Ambiente
   Edite o arquivo .env:
   # OBRIGATÓRIO: Sua API Key do OpenAI
   OPENAI_API_KEY=sk-your-actual-api-key-here

# OBRIGATÓRIO: API Key da aplicação (gere uma forte)
API_KEY=your-secret-api-key-here

# Database (ajuste se necessário)
DB_USERNAME=root
DB_PASSWORD=sua-senha-mysql

# FFmpeg (ajuste apenas se não estiver no PATH)
FFMPEG_PATH=ffmpeg
4. Gerar API Key Forte
   # Linux/Mac
   openssl rand -hex 32

# Windows (PowerShell)
[System.Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }))

# Online
# https://www.random.org/strings/

🏃 Executar a Aplicação
Desenvolvimento
# Linux/Mac
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
Produção
# Compilar
./mvnw clean package -DskipTests

# Executar JAR
java -jar target/transcription-api-0.0.1-SNAPSHOT.jar
Com Variáveis de Ambiente Inline
# Linux/Mac
API_KEY=your-key OPENAI_API_KEY=sk-xxx ./mvnw spring-boot:run

# Windows (PowerShell)
$env:API_KEY="your-key"; $env:OPENAI_API_KEY="sk-xxx"; ./mvnw spring-boot:run

✅ Verificar Instalação
1. Health Check
   curl http://localhost:8080/actuator/health
2. Testar Upload
   curl -X POST http://localhost:8080/api/transcriptions \
   -H "X-API-Key: your-api-key-here" \
   -F "file=@test-audio.mp3"
3. Verificar Logs
   # Deve aparecer:
# - Started TranscriptionApiApplication in X seconds
# - Hibernate: create table transcription_job
# - HikariPool started

🐳 Docker (Opcional)
Dockerfile
FROM openjdk:17-jdk-slim

# Instalar FFmpeg
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
docker-compose.yml
version: '3.8'

services:
mysql:
image: mysql:8.0
environment:
MYSQL_ROOT_PASSWORD: password
MYSQL_DATABASE: transcription_db
ports:
- "3306:3306"
volumes:
- mysql-data:/var/lib/mysql

app:
build: .
ports:
- "8080:8080"
environment:
API_KEY: your-api-key
OPENAI_API_KEY: sk-xxx
DB_USERNAME: root
DB_PASSWORD: password
DB_URL: jdbc:mysql://mysql:3306/transcription_db?createDatabaseIfNotExist=true
depends_on:
- mysql

volumes:
mysql-data:
Executar:
docker-compose up

🔍 Troubleshooting
Problema: "FFmpeg not found"
Solução:
# Verificar se está no PATH
ffmpeg -version

# Se não funcionar, forneça caminho completo no .env
FFMPEG_PATH=/usr/local/bin/ffmpeg  # Linux/Mac
FFMPEG_PATH=C:/ffmpeg/bin/ffmpeg.exe  # Windows
Problema: "Connection refused" (MySQL)
Solução:
# Verificar se MySQL está rodando
sudo systemctl status mysql  # Linux
brew services list  # Mac
Get-Service MySQL*  # Windows

# Iniciar MySQL
sudo systemctl start mysql  # Linux
brew services start mysql  # Mac
net start MySQL80  # Windows
Problema: "Access denied for user 'root'"
Solução:
# Resetar senha do MySQL
sudo mysql
ALTER USER 'root'@'localhost' IDENTIFIED BY 'nova-senha';
FLUSH PRIVILEGES;
Problema: "Public Key Retrieval is not allowed"
Solução: Já está corrigido no application.properties:
spring.datasource.url=...&allowPublicKeyRetrieval=true
Problema: "Invalid API Key"
Solução:
# Verificar se a variável está definida
echo $API_KEY  # Linux/Mac
echo $env:API_KEY  # Windows PowerShell

# Definir manualmente
export API_KEY=your-key  # Linux/Mac
$env:API_KEY="your-key"  # Windows

📊 Verificar Configuração
Script de Verificação (Linux/Mac)
#!/bin/bash

echo "=== Verificando Pré-requisitos ==="

# Java
if command -v java &> /dev/null; then
echo "✅ Java instalado: $(java -version 2>&1 | head -n 1)"
else
echo "❌ Java não encontrado"
fi

# MySQL
if command -v mysql &> /dev/null; then
echo "✅ MySQL instalado: $(mysql --version)"
else
echo "❌ MySQL não encontrado"
fi

# FFmpeg
if command -v ffmpeg &> /dev/null; then
echo "✅ FFmpeg instalado: $(ffmpeg -version | head -n 1)"
else
echo "❌ FFmpeg não encontrado"
fi

# Variáveis de ambiente
echo ""
echo "=== Variáveis de Ambiente ==="
[ -n "$API_KEY" ] && echo "✅ API_KEY definida" || echo "❌ API_KEY não definida"
[ -n "$OPENAI_API_KEY" ] && echo "✅ OPENAI_API_KEY definida" || echo "❌ OPENAI_API_KEY não definida"
[ -n "$DB_USERNAME" ] && echo "✅ DB_USERNAME definida" || echo "⚠️ DB_USERNAME não definida (usará padrão)"
Script de Verificação (Windows PowerShell)
Write-Host "=== Verificando Pré-requisitos ===" -ForegroundColor Cyan

# Java
if (Get-Command java -ErrorAction SilentlyContinue) {
Write-Host "✅ Java instalado" -ForegroundColor Green
} else {
Write-Host "❌ Java não encontrado" -ForegroundColor Red
}

# MySQL
if (Get-Command mysql -ErrorAction SilentlyContinue) {
Write-Host "✅ MySQL instalado" -ForegroundColor Green
} else {
Write-Host "❌ MySQL não encontrado" -ForegroundColor Red
}

# FFmpeg
if (Get-Command ffmpeg -ErrorAction SilentlyContinue) {
Write-Host "✅ FFmpeg instalado" -ForegroundColor Green
} else {
Write-Host "❌ FFmpeg não encontrado" -ForegroundColor Red
}

Write-Host "`n=== Variáveis de Ambiente ===" -ForegroundColor Cyan
if ($env:API_KEY) { Write-Host "✅ API_KEY definida" -ForegroundColor Green }
else { Write-Host "❌ API_KEY não definida" -ForegroundColor Red }

if ($env:OPENAI_API_KEY) { Write-Host "✅ OPENAI_API_KEY definida" -ForegroundColor Green }
else { Write-Host "❌ OPENAI_API_KEY não definida" -ForegroundColor Red }

🎯 Próximos Passos
Após configuração bem-sucedida:

✅ Testar endpoints básicos
✅ Fazer upload de áudio de teste
✅ Verificar logs
✅ Configurar backup do banco
✅ Configurar monitoramento
✅ Ler documentação da API


📚 Recursos Adicionais

Documentação Spring Boot
FFmpeg Documentation
OpenAI Whisper API
MySQL Documentation