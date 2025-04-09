package com.ssafy.ddukdoc.superapp.service;

import com.ssafy.ddukdoc.global.common.util.blockchain.BlockchainUtil;
import com.ssafy.ddukdoc.global.error.code.ErrorCode;
import com.ssafy.ddukdoc.global.error.exception.CustomException;
import com.ssafy.ddukdoc.superapp.dto.response.FileRegisterResultDto;
import com.ssafy.ddukdoc.superapp.util.MetadataAddUtil;
import com.ssafy.ddukdoc.domain.contract.dto.response.BlockchainDocumentResponseDto;
import com.ssafy.ddukdoc.global.common.util.HashUtil;
import com.ssafy.ddukdoc.global.common.util.blockchain.BlockchainUtil;
import com.ssafy.ddukdoc.global.error.code.ErrorCode;
import com.ssafy.ddukdoc.global.error.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenApiService {

    private final BlockchainUtil blockchainUtil;
    private final HashUtil hashUtil;
    private final FileValidationService fileValidationService;
    private final MetadataAddUtil metadataAddUtil;

    /**
     * SUPER APP 위변조 검증
     * @param file 검증할 파일 (PDF, WORD, PNG)
     */
    public void validationFile(MultipartFile file) {

        try{
            // 빈 파일인지 확인
            if(file == null || file.isEmpty()){
                throw new CustomException(ErrorCode.MATERIAL_UPLOAD_ERROR);
            }

            // 메타데이터에서 docName 추출
            String docName = fileValidationService.extractDocName(file);
            if(docName == null) {
                throw new CustomException(ErrorCode.VALIDATION_NOT_REGIST);
            }

            // docName으로 블록체인 Hash 조회
            BlockchainDocumentResponseDto blockchainDto = blockchainUtil.getDocumentByName(docName);

            String fileHash = "0x" + hashUtil.generateSHA256Hash(file.getBytes());

            // 블록체인 Hash값과 PDF Hash 비교
            if (!fileHash.equals(blockchainDto.getDocHash())) {
                throw new CustomException(ErrorCode.VALIDATION_NOT_MATCH, "docName", docName);
            }
        } catch (IOException e) {
            log.error("파일 검증 오류: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.VALIDATION_ERROR, "reason", e.getCause());
        }
    }
    // 지원하는 파일 확장자 목록
    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(
            Arrays.asList("pdf","png", "docx","doc")
    );
    @Transactional
    public FileRegisterResultDto registerFile(MultipartFile file) {

        // 빈 파일인 경우 체크
        if (file.isEmpty()) {
            throw new CustomException(ErrorCode.MATERIAL_UPLOAD_ERROR);
        }

        // 파일 이름 및 데이터 가져오기
        String filename = file.getOriginalFilename();

        // 파일 확장자 확인
        String extension = getFileExtension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new CustomException(ErrorCode.MATERIAL_INVALID_FORMAT);
        }

        // 메타데이터에 docName 추가
        Map<String,Object> dataWithMetadata = metadataAddUtil.processDocument(file);

        String docName = (String)dataWithMetadata.get("docName");
        byte[] pdfData = (byte[])dataWithMetadata.get("byteData");
        MediaType mediaType = determineMediaType(filename);
        try {
            // 블록체인에 해시값과 docName 저장
            blockchainUtil.saveDocumentInBlockchain(pdfData,null, docName,false);
        } catch (Exception e) {
            log.error("블록체인 저장 오류: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.BLOCKCHAIN_SAVE_ERROR, "블록체인에 저장 중 오류가 발생했습니다", e.getMessage());
        }

        // 응답 생성
        return FileRegisterResultDto.of(filename,mediaType,Base64.getEncoder().encodeToString(pdfData));
    }


    private MediaType determineMediaType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        switch (extension) {
            case "pdf":
                return MediaType.APPLICATION_PDF;
            case "doc":
            case "docx":
                return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "png":
                return MediaType.IMAGE_PNG;
            default:
                return MediaType.APPLICATION_OCTET_STREAM; // 기본 바이너리 타입
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new CustomException(ErrorCode.MATERIAL_INVALID_FORMAT, "file_name", filename);
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
