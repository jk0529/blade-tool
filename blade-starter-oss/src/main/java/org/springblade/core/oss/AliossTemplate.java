/**
 * Copyright (c) 2018-2099, Chill Zhuang 庄骞 (bladejava@qq.com).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springblade.core.oss;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springblade.core.oss.model.BladeFile;
import org.springblade.core.oss.model.OssFile;
import org.springblade.core.oss.props.OssProperties;
import org.springblade.core.oss.rule.OssRule;
import org.springblade.core.tool.jackson.JsonUtil;
import org.springblade.core.tool.utils.StringPool;
import org.springblade.core.tool.utils.StringUtil;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PolicyConditions;
import com.aliyun.oss.model.PutObjectResult;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

/**
 * AliossTemplate
 *
 * @author Chill
 */
@AllArgsConstructor
public class AliossTemplate implements OssTemplate {
	private final OSSClient ossClient;
	private final OssProperties ossProperties;
	private final OssRule ossRule;

	@Override
	@SneakyThrows
	public void makeBucket(String bucketName) {
		if (!bucketExists(bucketName)) {
			ossClient.createBucket(getBucketName(bucketName));
		}
	}

	@Override
	@SneakyThrows
	public void removeBucket(String bucketName) {
		ossClient.deleteBucket(getBucketName(bucketName));
	}

	@Override
	@SneakyThrows
	public boolean bucketExists(String bucketName) {
		return ossClient.doesBucketExist(getBucketName(bucketName));
	}

	@Override
	@SneakyThrows
	public void copyFile(String bucketName, String fileName, String destBucketName) {
		ossClient.copyObject(getBucketName(bucketName), fileName, getBucketName(destBucketName), fileName);
	}

	@Override
	@SneakyThrows
	public void copyFile(String bucketName, String fileName, String destBucketName, String destFileName) {
		ossClient.copyObject(getBucketName(bucketName), fileName, getBucketName(destBucketName), destFileName);
	}

	@Override
	@SneakyThrows
	public OssFile statFile(String fileName) {
		return statFile(ossProperties.getBucketName(), fileName);
	}

	@Override
	@SneakyThrows
	public OssFile statFile(String bucketName, String fileName) {
		ObjectMetadata stat = ossClient.getObjectMetadata(getBucketName(bucketName), fileName);
		OssFile ossFile = new OssFile();
		ossFile.setName(fileName);
		ossFile.setLink(fileLink(ossFile.getName()));
		ossFile.setHash(stat.getContentMD5());
		ossFile.setLength(stat.getContentLength());
		ossFile.setPutTime(stat.getLastModified());
		ossFile.setContentType(stat.getContentType());
		return ossFile;
	}

	@Override
	@SneakyThrows
	public String filePath(String fileName) {
		return getOssHost().concat(StringPool.SLASH).concat(fileName);
	}

	@Override
	@SneakyThrows
	public String filePath(String bucketName, String fileName) {
		return getOssHost(bucketName).concat(StringPool.SLASH).concat(fileName);
	}

	@Override
	@SneakyThrows
	public String fileLink(String fileName) {
		return getOssHost().concat(StringPool.SLASH).concat(fileName);
	}

	@Override
	@SneakyThrows
	public String fileLink(String bucketName, String fileName) {
		return getOssHost(bucketName).concat(StringPool.SLASH).concat(fileName);
	}

	@Override
	@SneakyThrows
	public BladeFile putFile(MultipartFile file) {
		return putFile(ossProperties.getBucketName(), file.getOriginalFilename(), file);
	}

	@Override
	@SneakyThrows
	public BladeFile putFile(String fileName, MultipartFile file) {
		return putFile(ossProperties.getBucketName(), fileName, file);
	}

	@Override
	@SneakyThrows
	public BladeFile putFile(String bucketName, String fileName, MultipartFile file) {
		return putFile(bucketName, fileName, file.getInputStream());
	}

	@Override
	@SneakyThrows
	public BladeFile putFile(String fileName, InputStream stream) {
		return putFile(ossProperties.getBucketName(), fileName, stream);
	}

	@Override
	@SneakyThrows
	public BladeFile putFile(String bucketName, String fileName, InputStream stream) {
		return put(bucketName, stream, fileName, false);
	}

	/**
	 * 上传文件到OSS
	 *
	 * @param bucketName 存储桶名称
	 * @param stream     输入流
	 * @param key       文件名
	 * @param cover     是否覆盖上传
	 * @return BladeFile 上传文件信息
	 */
	@SneakyThrows
	public BladeFile put(String bucketName, InputStream stream, String key, boolean cover) {
		makeBucket(bucketName);
		String originalName = key;
		key = getFileName(key);
		// 覆盖上传
		if (cover) {
			ossClient.putObject(getBucketName(bucketName), key, stream);
		} else {
			PutObjectResult response = ossClient.putObject(getBucketName(bucketName), key, stream);
			int retry = 0;
			int retryCount = 5;
			while (StringUtils.isEmpty(response.getETag()) && retry < retryCount) {
				response = ossClient.putObject(getBucketName(bucketName), key, stream);
				retry++;
			}
		}
		BladeFile file = new BladeFile();
		file.setOriginalName(originalName);
		file.setName(key);
		file.setDomain(getOssHost(bucketName));
		file.setLink(fileLink(bucketName, key));
		return file;
	}

	@Override
	@SneakyThrows
	public void removeFile(String fileName) {
		ossClient.deleteObject(getBucketName(), fileName);
	}

	@Override
	@SneakyThrows
	public void removeFile(String bucketName, String fileName) {
		ossClient.deleteObject(getBucketName(bucketName), fileName);
	}

	@Override
	@SneakyThrows
	public void removeFiles(List<String> fileNames) {
		fileNames.forEach(this::removeFile);
	}

	@Override
	@SneakyThrows
	public void removeFiles(String bucketName, List<String> fileNames) {
		fileNames.forEach(fileName -> removeFile(getBucketName(bucketName), fileName));
	}

	/**
	 * 获取默认存储桶名称
	 *
	 * @return String 存储桶名称
	 */
	private String getBucketName() {
		return getBucketName(ossProperties.getBucketName());
	}

	/**
	 * 根据规则生成存储桶名称
	 *
	 * @param bucketName 存储桶名称
	 * @return String 处理后的存储桶名称
	 */
	private String getBucketName(String bucketName) {
		return ossRule.bucketName(bucketName);
	}

	/**
	 * 根据规则生成文件名称
	 *
	 * @param originalFilename 原始文件名
	 * @return String 处理后的文件名
	 */
	private String getFileName(String originalFilename) {
		return ossRule.fileName(originalFilename);
	}

	/**
	 * 获取默认存储桶的上传凭证
	 *
	 * @return String 上传凭证
	 */
	public String getUploadToken() {
		return getUploadToken(ossProperties.getBucketName());
	}

	/**
	 * 获取指定存储桶的上传凭证
	 *
	 * @param bucketName 存储桶名称
	 * @return String 上传凭证
	 */
	public String getUploadToken(String bucketName) {
		// 默认过期时间2小时
		return getUploadToken(bucketName, ossProperties.getArgs().get("expireTime", 3600L));
	}

	/**
	 * 获取带过期时间的上传凭证
	 *
	 * @param bucketName 存储桶名称
	 * @param expireTime 过期时间（秒）
	 * @return String 上传凭证
	 */
	public String getUploadToken(String bucketName, long expireTime) {
		String baseDir = "upload";

		long expireEndTime = System.currentTimeMillis() + expireTime * 1000;
		Date expiration = new Date(expireEndTime);

		PolicyConditions policyConds = new PolicyConditions();
		// 默认大小限制10M
		policyConds.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, ossProperties.getArgs().get("contentLengthRange", 10485760));
		policyConds.addConditionItem(MatchMode.StartWith, PolicyConditions.COND_KEY, baseDir);

		String postPolicy = ossClient.generatePostPolicy(expiration, policyConds);
		byte[] binaryData = postPolicy.getBytes(StandardCharsets.UTF_8);
		String encodedPolicy = BinaryUtil.toBase64String(binaryData);
		String postSignature = ossClient.calculatePostSignature(postPolicy);

		Map<String, String> respMap = new LinkedHashMap<>(16);
		respMap.put("accessid", ossProperties.getAccessKey());
		respMap.put("policy", encodedPolicy);
		respMap.put("signature", postSignature);
		respMap.put("dir", baseDir);
		respMap.put("host", getOssHost(bucketName));
		respMap.put("expire", String.valueOf(expireEndTime / 1000));
		return JsonUtil.toJson(respMap);
	}

	/**
	 * 获取指定存储桶的OSS访问域名
	 *
	 * @param bucketName 存储桶名称
	 * @return String OSS访问域名
	 */
	public String getOssHost(String bucketName) {
		String prefix = getEndpoint().contains("https://") ? "https://" : "http://";
		return prefix + getBucketName(bucketName) + StringPool.DOT + getEndpoint().replaceFirst(prefix, StringPool.EMPTY);
	}

	/**
	 * 获取默认存储桶的OSS访问域名
	 *
	 * @return String OSS访问域名
	 */
	public String getOssHost() {
		return getOssHost(ossProperties.getBucketName());
	}

	/**
	 * 获取OSS服务的Endpoint
	 *
	 * @return String OSS Endpoint
	 */
	public String getEndpoint() {
		if (StringUtil.isBlank(ossProperties.getTransformEndpoint())) {
			return ossProperties.getEndpoint();
		}
		return ossProperties.getTransformEndpoint();
	}

}
