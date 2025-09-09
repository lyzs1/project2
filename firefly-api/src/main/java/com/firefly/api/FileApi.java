package com.firefly.api;

import com.firefly.domain.JsonResponse;
import com.firefly.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileApi {

    @Autowired
    private FileService fileService;

    //作用：由前端调用算出file的fileMd5，其作为分片上传时 redis中的每个slice的key
    //md5密文不受文件名称与后缀影响
    @PostMapping("/md5files")
    public JsonResponse<String> getFileMD5(MultipartFile file) throws Exception {
        String fileMD5 = fileService.getFileMD5(file);
        return new JsonResponse<>(fileMD5);
    }

    //分片上传 ——> 上传相同file可实现秒传 + 断点续传
    //slice：当前这片分片
    //fileMd5：整个file的 MD5
    //sliceNo：当前分片序号
    //totalSliceNo：总分片数
    @PutMapping("/file-slices")
    public JsonResponse<String> uploadFileBySlices(MultipartFile slice,
                                                   String fileMd5,
                                                   Integer sliceNo,
                                                   Integer totalSliceNo) throws Exception {
        String filePath = fileService.uploadFileBySlices(slice, fileMd5, sliceNo, totalSliceNo);
        return new JsonResponse<>(filePath);
    }

}
