package com.xunim.transcriptionapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para estatísticas de jobs de transcrição
 *
 * Alternativa ao record (para compatibilidade com Java < 17)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatistics {

    private long total;
    private long processing;
    private long completed;
    private long errors;

    /**
     * Calcula taxa de sucesso em porcentagem
     */
    public double getSuccessRate() {
        if (total == 0) return 0.0;
        return Math.round((double) completed / total * 10000.0) / 100.0;
    }

    /**
     * Calcula taxa de erro em porcentagem
     */
    public double getErrorRate() {
        if (total == 0) return 0.0;
        return Math.round((double) errors / total * 10000.0) / 100.0;
    }

    /**
     * Verifica se há jobs em processamento
     */
    public boolean hasJobsProcessing() {
        return processing > 0;
    }

    /**
     * Retorna jobs que estão aguardando (uploaded mas não processando)
     */
    public long getPending() {
        return total - processing - completed - errors;
    }
}