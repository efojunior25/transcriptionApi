package com.xunim.transcriptionapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Habilita o agendamento de tarefas (@Scheduled)
}