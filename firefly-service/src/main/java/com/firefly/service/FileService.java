package com.firefly.service;

import com.firefly.dao.FileDao;
import com.firefly.domain.File;
import com.firefly.service.util.FastDFSUtil;
import com.firefly.service.util.MD5Util;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;

@Service
public class FileService {

    @Autowired
    private FileDao fileDao;

    @Autowired
    private FastDFSUtil fastDFSUtil;

    public String uploadFileBySlices(MultipartFile slice,
                                         String fileMD5,
                                         Integer sliceNo,
                                         Integer totalSliceNo) throws Exception {
        File dbFileMD5 = fileDao.getFileByMD5(fileMD5);
        //秒传功能；fileMD5已存在，直接返回数据库里已有的 url
        if(dbFileMD5 != null){
            return dbFileMD5.getUrl();
        }
        //否则进入分片接收流程
        String url = fastDFSUtil.uploadFileBySlices(slice, fileMD5, sliceNo, totalSliceNo);
        //fdfs返回对应分片的fdfs存储url后，将其落库
        if(!StringUtil.isNullOrEmpty(url)){
            dbFileMD5 = new File();
            dbFileMD5.setCreateTime(new Date());
            dbFileMD5.setMd5(fileMD5);
            dbFileMD5.setUrl(url);
            dbFileMD5.setType(fastDFSUtil.getFileType(slice));
            fileDao.addFile(dbFileMD5);
        }
        return url;
    }

    public String getFileMD5(MultipartFile file) throws Exception {
        return MD5Util.getFileMD5(file);
    }

    public File getFileByMd5(String fileMd5) {
        return fileDao.getFileByMD5(fileMd5);
    }
}
