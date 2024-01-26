package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;


public interface HosService {

    BaseResult getFileList(String bucketName, String prefix, Map<String, String> udfMetaParam);

    BaseResult uploadFile(String bucketName, String fileName, MultipartFile file);

    BaseResult deleteFileList(String bucketName, List<String> collect);

    BaseResult getFile(String bucketName, String fileName);
}
