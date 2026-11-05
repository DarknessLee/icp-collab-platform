package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.common.storage.FileStorage;
import com.icp.system.entity.SysFile;
import com.icp.system.mapper.SysFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 附件单测：扩展名白名单、大小上限（sys_config）、成功落库字段。
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private SysFileMapper fileMapper;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private ConfigService configService;
    @Mock
    private MultipartFile multipartFile;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileMapper, fileStorage, configService);
        lenient().when(configService.get("sys.upload.allowExt")).thenReturn(Optional.empty());
        lenient().when(configService.get("sys.file.storageType")).thenReturn(Optional.of("local"));
        lenient().when(configService.getInt("sys.upload.maxSize", 20)).thenReturn(20);
        lenient().when(multipartFile.isEmpty()).thenReturn(false);
    }

    @Test
    void unsupportedSuffixRejected() {
        when(multipartFile.getOriginalFilename()).thenReturn("virus.exe");

        assertThatThrownBy(() -> fileService.store(multipartFile, "test", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_INVALID));
    }

    @Test
    void oversizedFileRejected() {
        when(multipartFile.getOriginalFilename()).thenReturn("big.pdf");
        when(multipartFile.getSize()).thenReturn(21L * 1024 * 1024);

        assertThatThrownBy(() -> fileService.store(multipartFile, "test", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_INVALID));
    }

    @Test
    void validFileStoredWithMetadata() {
        when(multipartFile.getOriginalFilename()).thenReturn("制度文件.pdf");
        when(multipartFile.getSize()).thenReturn(1024L);
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(fileStorage.store(multipartFile, "policy")).thenReturn("policy/2026/11/03/uuid.pdf");
        SysFile saved = fileService.store(multipartFile, "policy", 1101L);

        assertThat(saved.getFileName()).isEqualTo("制度文件.pdf");
        assertThat(saved.getFileSuffix()).isEqualTo("pdf");
        assertThat(saved.getFilePath()).isEqualTo("policy/2026/11/03/uuid.pdf");
        assertThat(saved.getFileSize()).isEqualTo(1024L);
        assertThat(saved.getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getBizType()).isEqualTo("policy");
        assertThat(saved.getBizId()).isEqualTo(1101L);
    }

    @Test
    void emptyFileRejected() {
        when(multipartFile.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> fileService.store(multipartFile, "test", null))
                .isInstanceOf(BusinessException.class);
    }
}
