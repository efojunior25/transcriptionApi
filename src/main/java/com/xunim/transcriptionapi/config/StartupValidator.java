package com.xunim.transcriptionapi.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida todas as configurações necessárias na inicialização da aplicação
 *
 * Se alguma configuração crítica estiver faltando, a aplicação NÃO inicia.
 * Isso evita que a aplicação suba mas não funcione.
 *
 * ✅ VALIDAÇÕES REALIZADAS:
 * - API Key da aplicação configurada
 * - OpenAI API Key configurada e no formato correto
 * - FFmpeg instalado e acessível
 * - Diretório de uploads criado e gravável
 * - Configurações de banco de dados presentes
 */
@Component
@Slf4j
public class StartupValidator {

    @Value("${api.security.api-key}")
    private String apiKey;

    @Value("${openai.api.key}")
    private String openaiKey;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${storage.upload-dir}")
    private String uploadDir;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @PostConstruct
    public void validateConfiguration() {
        log.info("🔍 Validando configurações da aplicação...");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ==================== VALIDAÇÕES CRÍTICAS ====================

        // 1. API Key da Aplicação
        if (!isValidApiKey(apiKey)) {
            errors.add("❌ TRANSCRIPTION_API_KEY não configurada ou inválida! " +
                    "Defina a variável de ambiente TRANSCRIPTION_API_KEY com uma chave forte.");
        } else {
            log.info("✅ API Key da aplicação configurada ({}...)", apiKey.substring(0, Math.min(8, apiKey.length())));
        }

        // 2. OpenAI API Key
        if (!isValidOpenAIKey(openaiKey)) {
            errors.add("❌ OPENAI_API_KEY não configurada ou inválida! " +
                    "Defina a variável de ambiente OPENAI_API_KEY. " +
                    "Obtenha em: https://platform.openai.com/api-keys");
        } else {
            log.info("✅ OpenAI API Key configurada (sk-...{}})", openaiKey.substring(openaiKey.length() - 4));
        }

        // 3. FFmpeg
        if (!isFfmpegAvailable(ffmpegPath)) {
            errors.add("❌ FFmpeg não encontrado! Caminho configurado: " + ffmpegPath + ". " +
                    "Instale FFmpeg ou corrija a variável FFMPEG_PATH.");
        } else {
            String version = getFfmpegVersion(ffmpegPath);
            log.info("✅ FFmpeg disponível: {}", version != null ? version : "versão desconhecida");
        }

        // 4. Diretório de Uploads
        if (!validateUploadDirectory(uploadDir)) {
            errors.add("❌ Não foi possível criar/acessar o diretório de uploads: " + uploadDir);
        } else {
            log.info("✅ Diretório de uploads configurado: {}", uploadDir);
        }

        // 5. Configuração de Banco de Dados
        if (!isValidDatasourceConfig(datasourceUrl, datasourceUsername, datasourcePassword)) {
            errors.add("❌ Configuração de banco de dados incompleta! " +
                    "Verifique DB_USERNAME e DB_PASSWORD.");
        } else {
            log.info("✅ Configuração de banco de dados presente");
        }

        // ==================== VALIDAÇÕES DE AVISO ====================

        // API Key muito fraca
        if (apiKey != null && apiKey.length() < 32) {
            warnings.add("⚠️ API Key muito curta (< 32 caracteres). Recomendado: 64+ caracteres");
        }

        // Show SQL em produção
        if (isProductionProfile() && isSqlLoggingEnabled()) {
            warnings.add("⚠️ SQL logging habilitado em produção. Desabilite para melhor performance.");
        }

        // ==================== RESULTADO ====================

        // Exibe avisos
        if (!warnings.isEmpty()) {
            log.warn("═══════════════════════════════════════════════════");
            log.warn("⚠️ AVISOS DE CONFIGURAÇÃO:");
            warnings.forEach(log::warn);
            log.warn("═══════════════════════════════════════════════════");
        }

        // Se há erros críticos, IMPEDE a inicialização
        if (!errors.isEmpty()) {
            log.error("═══════════════════════════════════════════════════");
            log.error("❌ ERRO DE CONFIGURAÇÃO - APLICAÇÃO NÃO PODE INICIAR");
            log.error("═══════════════════════════════════════════════════");
            errors.forEach(log::error);
            log.error("═══════════════════════════════════════════════════");
            log.error("💡 DICA: Copie o arquivo .env.example e configure as variáveis necessárias");
            log.error("═══════════════════════════════════════════════════");

            throw new IllegalStateException(
                    "Falha na validação de configuração. Verifique os logs acima. " +
                            "Total de erros: " + errors.size()
            );
        }

        log.info("═══════════════════════════════════════════════════");
        log.info("✅ TODAS AS CONFIGURAÇÕES VALIDADAS COM SUCESSO!");
        log.info("═══════════════════════════════════════════════════");
    }

    // ==================== Métodos de Validação ====================

    private boolean isValidApiKey(String key) {
        return key != null &&
                !key.trim().isEmpty() &&
                !key.contains("${") &&  // Não foi substituída
                !key.equals("your-api-key-here") &&
                !key.equals("change-me");
    }

    private boolean isValidOpenAIKey(String key) {
        return key != null &&
                key.startsWith("sk-") &&
                key.length() > 20 &&
                !key.contains("${");
    }

    private boolean isFfmpegAvailable(String path) {
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("FFmpeg não disponível: {}", e.getMessage());
            return false;
        }
    }

    private String getFfmpegVersion(String path) {
        try {
            ProcessBuilder pb = new ProcessBuilder(path, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            byte[] output = process.getInputStream().readAllBytes();
            String versionOutput = new String(output);

            // Pega primeira linha (contém a versão)
            String firstLine = versionOutput.split("\n")[0];
            return firstLine.trim();

        } catch (Exception e) {
            return null;
        }
    }

    private boolean validateUploadDirectory(String dir) {
        try {
            Path path = Paths.get(dir);

            // Cria diretório se não existir
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("📁 Diretório de uploads criado: {}", path.toAbsolutePath());
            }

            // Verifica se é diretório
            if (!Files.isDirectory(path)) {
                log.error("{} existe mas não é um diretório", path);
                return false;
            }

            // Verifica permissões de escrita
            if (!Files.isWritable(path)) {
                log.error("Sem permissão de escrita em {}", path);
                return false;
            }

            // Testa criação de arquivo temporário
            Path testFile = path.resolve(".write-test-" + System.currentTimeMillis());
            Files.writeString(testFile, "test");
            Files.delete(testFile);

            return true;

        } catch (Exception e) {
            log.error("Erro ao validar diretório de uploads: {}", e.getMessage());
            return false;
        }
    }

    private boolean isValidDatasourceConfig(String url, String username, String password) {
        return url != null && !url.contains("${") &&
                username != null && !username.contains("${") &&
                password != null && !password.contains("${");
    }

    private boolean isProductionProfile() {
        String activeProfile = System.getProperty("spring.profiles.active", "");
        return activeProfile.contains("prod") || activeProfile.contains("production");
    }

    private boolean isSqlLoggingEnabled() {
        // Verifica se spring.jpa.show-sql está true
        String showSql = System.getProperty("spring.jpa.show-sql", "false");
        return "true".equalsIgnoreCase(showSql);
    }
}