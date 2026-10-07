package com.personal.finance.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.finance.backup.dto.BackupData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Writes a full JSON snapshot to disk every night so a crash never destroys
 * more than one day of data. Failures are logged and never crash the process.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;
    private final ObjectMapper objectMapper;

    @Value("${app.backup.dir:./backups}")
    private String backupDir;

    @Scheduled(cron = "0 30 2 * * *")
    @SchedulerLock(name = "automaticBackup", lockAtMostFor = "PT30M")
    public void createAutomaticBackup() {
        try {
            Path dir = Path.of(backupDir);
            Files.createDirectories(dir);
            BackupData data = backupService.exportData();
            Path target = dir.resolve("finance-backup-" + LocalDate.now() + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), data);
            log.info("Automatic backup written to {}", target.toAbsolutePath());
        } catch (Exception ex) {
            log.error("Automatic backup failed", ex);
        }
    }
}
