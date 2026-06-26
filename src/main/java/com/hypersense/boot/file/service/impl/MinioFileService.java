package com.hypersense.boot.file.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.hypersense.boot.common.exception.BusinessException;
import com.hypersense.boot.common.result.ResultCode;
import com.hypersense.boot.file.model.FileInfo;
import com.hypersense.boot.file.service.FileService;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * MinIO 文件上传服务类
 *
 * @author Ray.Hao
 * @since 2023/6/2
 */
@Component
@ConditionalOnProperty(value = "oss.type", havingValue = "minio")
@ConfigurationProperties(prefix = "oss.minio")
@RequiredArgsConstructor
@Data
@Slf4j
public class MinioFileService implements FileService {

    /**
     * 服务Endpoint
     */
    private String endpoint;
    /**
     * 访问凭据
     */
    private String accessKey;
    /**
     * 凭据密钥
     */
    private String secretKey;
    /**
     * 存储桶名称
     */
    private String bucketName;
    /**
     * 自定义域名
     */
    private String customDomain;

    private MinioClient minioClient;

    // 依赖注入完成之后执行初始化
    @PostConstruct
    public void init() {
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        // 创建存储桶(存储桶不存在)
        // createBucketIfAbsent(bucketName);
    }


    /**
     * 上传文件
     *
     * @param file 表单文件对象
     * @return 文件信息
     */
    @Override
    public FileInfo uploadFile(MultipartFile file) {

        // 创建存储桶(存储桶不存在)，如果有搭建好的minio服务，建议放在init方法中
        createBucketIfAbsent(bucketName);

        // 文件原生名称
        String originalFilename = file.getOriginalFilename();
        // 文件后缀
        String suffix = FileUtil.getSuffix(originalFilename);
        // 文件夹名称
        String dateFolder = DateUtil.format(LocalDateTime.now(), "yyyyMMdd");
        // 文件名称
        String fileName = IdUtil.simpleUUID() + "." + suffix;

        //  try-with-resource 语法糖自动释放流
        try (InputStream inputStream = file.getInputStream()) {
            // 文件上传
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(dateFolder + "/"+ fileName)
                    .contentType(file.getContentType())
                    .stream(inputStream, inputStream.available(), -1)
                    .build();
            minioClient.putObject(putObjectArgs);

            // 返回文件路径
            String fileUrl;
            // 未配置自定义域名
            if (StrUtil.isBlank(customDomain)) {
                // 获取文件URL
                GetPresignedObjectUrlArgs getPresignedObjectUrlArgs = GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(dateFolder + "/"+ fileName)
                        .method(Method.GET)
                        .build();

                fileUrl = minioClient.getPresignedObjectUrl(getPresignedObjectUrlArgs);
                fileUrl = fileUrl.substring(0, fileUrl.indexOf("?"));
            } else {
                // 配置自定义文件路径域名
                fileUrl = customDomain + "/"+ bucketName + "/"+ dateFolder + "/"+ fileName;
            }

            FileInfo fileInfo = new FileInfo();
            fileInfo.setName(originalFilename);
            fileInfo.setUrl(fileUrl);
            return fileInfo;
        } catch (Exception e) {
            log.error("上传文件失败", e);
            throw new BusinessException(ResultCode.UPLOAD_FILE_EXCEPTION, e.getMessage());
        }
    }


    /**
     * 以输入流方式上传文件，并指定对象 key（不做 UUID 重命名）。
     *
     * <p>用于把已经知道目标路径的流式内容（例如场景模板的 example.html）上传到固定 key。</p>
     * <p>注意：此方法未加入 {@link FileService} 接口，避免阿里云/本地实现被迫新增空实现。
     * 调用方需直接注入 {@link MinioFileService}（minio 模式下唯一存在的 FileService Bean）。</p>
     *
     * @param inputStream 输入流（调用方负责关闭）
     * @param size        字节数；未知时传 -1，由内部按分片读取
     * @param objectKey   对象 key（不含 bucketName），例如 {@code scene-template/0/blog-post.html}
     * @param contentType MIME，例如 {@code text/html}
     * @return 可访问的 URL（与 {@link #uploadFile(MultipartFile)} 返回值格式一致）
     */
    public String uploadStream(InputStream inputStream, long size, String objectKey, String contentType) {
        createBucketIfAbsent(bucketName);
        try {
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .contentType(contentType)
                    // size 为 -1 时 MinIO 会以分片方式读取（5MiB partSize）
                    .stream(inputStream, size, size < 0 ? 10 * 1024 * 1024 : -1)
                    .build();
            minioClient.putObject(putObjectArgs);

            String fileUrl;
            if (StrUtil.isBlank(customDomain)) {
                GetPresignedObjectUrlArgs getPresignedObjectUrlArgs = GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .method(Method.GET)
                        .build();
                fileUrl = minioClient.getPresignedObjectUrl(getPresignedObjectUrlArgs);
                fileUrl = fileUrl.substring(0, fileUrl.indexOf("?"));
            } else {
                fileUrl = customDomain + "/" + bucketName + "/" + objectKey;
            }
            return fileUrl;
        } catch (Exception e) {
            log.error("上传流失败, objectKey={}", objectKey, e);
            throw new BusinessException(ResultCode.UPLOAD_FILE_EXCEPTION, e.getMessage());
        }
    }

    /**
     * 删除文件
     *
     * @param filePath 文件完整路径
     * @return 是否删除成功
     */
    @Override
    public boolean deleteFile(String filePath) {
        Assert.notBlank(filePath, "删除文件路径不能为空");
        try {
            String fileName;
            if (StrUtil.isNotBlank(customDomain)) {
                // https://oss.youlai.tech/default/20221120/test.jpg → 20221120/websocket.jpg
                fileName = filePath.substring(customDomain.length() + 1 + bucketName.length() + 1); // 两个/占了2个字符长度
            } else {
                // http://localhost:9000/default/20221120/test.jpg → 20221120/websocket.jpg
                fileName = filePath.substring(endpoint.length() + 1 + bucketName.length() + 1);
            }
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build();

            minioClient.removeObject(removeObjectArgs);
            return true;
        } catch (Exception e) {
            log.error("删除文件失败", e);
            throw new BusinessException(ResultCode.DELETE_FILE_EXCEPTION, e.getMessage());
        }
    }


    /**
     * PUBLIC桶策略
     * 如果不配置，则新建的存储桶默认是PRIVATE，则存储桶文件会拒绝访问 Access Denied
     *
     * @param bucketName 存储桶名称
     * @return 存储桶策略
     */
    private static String publicBucketPolicy(String bucketName) {
        // AWS的S3存储桶策略 JSON 格式 https://docs.aws.amazon.com/zh_cn/AmazonS3/latest/userguide/example-bucket-policies.html
        return "{\"Version\":\"2012-10-17\","
                + "\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:ListBucketMultipartUploads\",\"s3:GetBucketLocation\",\"s3:ListBucket\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucketName + "\"]},"
                + "{\"Effect\":\"Allow\"," + "\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:ListMultipartUploadParts\",\"s3:PutObject\",\"s3:AbortMultipartUpload\",\"s3:DeleteObject\",\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucketName + "/*\"]}]}";
    }

    /**
     * 创建存储桶(存储桶不存在)
     *
     * @param bucketName 存储桶名称
     */
    @SneakyThrows
    private void createBucketIfAbsent(String bucketName) {
        BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(bucketName).build();
        if (!minioClient.bucketExists(bucketExistsArgs)) {
            MakeBucketArgs makeBucketArgs = MakeBucketArgs.builder().bucket(bucketName).build();

            minioClient.makeBucket(makeBucketArgs);

            // 设置存储桶访问权限为PUBLIC， 如果不配置，则新建的存储桶默认是PRIVATE，则存储桶文件会拒绝访问 Access Denied
            SetBucketPolicyArgs setBucketPolicyArgs = SetBucketPolicyArgs
                    .builder()
                    .bucket(bucketName)
                    .config(publicBucketPolicy(bucketName))
                    .build();
            minioClient.setBucketPolicy(setBucketPolicyArgs);
        }
    }
}
