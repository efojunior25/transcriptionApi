package com.xunim.transcriptionapi.controller;

import com.xunim.transcriptionapi.model.TranscriptionJob;
import com.xunim.transcriptionapi.service.TranscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/transcriptions")
@RequiredArgsConstructor
public class TranscriptionController {

    private final TranscriptionService service;

    @PostMapping
    public ResponseEntity<TranscriptionJob> upload(
            @RequestParam("file") MultipartFile file) throws IOException {

        return ResponseEntity.ok(service.createJob(file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TranscriptionJob> status(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }
}

