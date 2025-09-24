package com.ke.bella.files.service;

import static com.ke.bella.files.db.IDGenerator.FILE_ID_GENERATOR;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.files.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ke.bella.files.FileShardingCountUpdator;
import com.ke.bella.files.TaskExecutor;
import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileProgressDB;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.protocol.BroadcastStatus;
import com.ke.bella.files.protocol.EventType;
import com.ke.bella.files.protocol.FileBroadcasting;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.Page;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.files.protocol.Progress;
import com.ke.bella.files.protocol.Scope;
import com.ke.bella.files.protocol.UpdateProgressRequestData;
import com.ke.bella.files.protocol.FileCountInfo;
import com.ke.bella.files.service.broadcast.BroadcastService;
import com.ke.bella.files.service.storage.StorageService;
import com.ke.bella.files.utils.BellaContextHelper;
import com.ke.bella.files.utils.FilePurposeClassifier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FileService {
    public static final Long ONE_DAY = 24 * 60 * 60L;
    public static final String ONE_DAY_STRING = (24 * 60 * 60) + "";
    private static final String VISION = "vision";
    @Autowired
    private FileRepo fileRepo;
    @Autowired
    private StorageService storageService;
    @Autowired
    private BucketConfig bucketConfig;
    @Autowired
    private BroadcastService broadcastService;
    @Autowired
    private FileShardingCountUpdator fileShardingCountUpdator;

    public boolean exists(String spaceCode, String ancestorId, String filename) {
        return fileRepo.exists(spaceCode, ancestorId, filename);
    }

    public OpenAIFile getFile(String spaceCode, String ancestorId, String filename) {
        FileDB fileDB = fileRepo.queryFile(spaceCode, ancestorId, filename);
        if(fileDB == null) {
            return null;
        }
        return transferToOpenAIFile(fileDB);
    }

	/**
	 * 根据space_code在根目录下查找文件（只查第一层）
	 */
	public OpenAIFile getFileByNameInSpace(String spaceCode, String filename) {
		return getFile(spaceCode, null, filename);
	}

	/**
	 * 根据ancestor_id在第一层子目录中查找文件
	 */
	public OpenAIFile getFileByNameInDirectory(String ancestorId, String filename) {
		// 先获取ancestorId对应的文件信息以获取spaceCode
		FileDB ancestorFileDB = fileRepo.queryFile(ancestorId);
		if(ancestorFileDB == null) {
			return null;
		}
		return getFile(ancestorFileDB.getSpaceCode(), ancestorId, filename);
	}

    private void updateBroadcastStatus(String fileId, BroadcastStatus status) {
        FileOps op = new FileOps();
        op.setFileId(fileId);
        op.setBroadcastStatus(status);
        try {
            fileRepo.updateFile(op);
        } catch (Exception e) {
            String msg = "Failed to update file broadcast status, fileId: "
                    + fileId + ", broadcastStatus: " + status.getValue();
            LOGGER.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }

    private OpenAIFile transferToOpenAIFile(FileDB fileDB) {
        return OpenAIFile.builder()
                .id(fileDB.getFileId())
                .bytes(fileDB.getBytes())
                .createdAt(fileDB.getCtime()
                        .toInstant(ZoneId.systemDefault().getRules().getOffset(fileDB.getCtime()))
                        .toEpochMilli())
                .filename(fileDB.getFilename())
                .isDir(fileDB.getIsDir() == 1)
                .extension(fileDB.getExtension())
                .mimeType(fileDB.getMimeType())
                .type(fileDB.getType())
                .purpose(fileDB.getPurpose())
                .domTreeFileId(fileDB.getDomTreeFileId())
                .pdfFileId(fileDB.getPdfFileId())
                .version(fileDB.getVersion())
                .spaceCode(fileDB.getSpaceCode())
                .metadata(fileDB.getMetaData())
                .cuid(fileDB.getCuid())
                .cuName(fileDB.getCuName())
			.muid(fileDB.getMuid())
			.muName(fileDB.getMuName())
			.mtime(fileDB.getMtime()
				.toInstant(ZoneId.systemDefault().getRules().getOffset(fileDB.getMtime()))
				.toEpochMilli())
			.description(fileDB.getDescription())
			.cities(StringUtils.isNotEmpty(fileDB.getCities()) ?
				JsonUtils.fromJson(fileDB.getCities(), new TypeReference<List<String>>() {
				}) :
				new ArrayList<>())
			.tags(StringUtils.isNotEmpty(fileDB.getTags()) ?
				JsonUtils.fromJson(fileDB.getTags(), new TypeReference<List<String>>() {
				}) :
				new ArrayList<>())
                .build();
    }

    private OpenAIFile buildOpenAIFileWithSource(FileDB fileDB) {
        OpenAIFile openAIFile = transferToOpenAIFile(fileDB);
        String purpose = fileDB.getPurpose();

        FileDB sourceFileDB = null;
        try {
            if("dom_tree".equals(purpose)) {
                sourceFileDB = fileRepo.queryFileByDomTreeFileId(fileDB.getFileId());
            } else if("pdf".equals(purpose)) {
                sourceFileDB = fileRepo.queryFileByPdfFileId(fileDB.getFileId());
            }

            if(sourceFileDB != null) {
                return openAIFile.toBuilder().sourceFile(transferToOpenAIFile(sourceFileDB)).build();
            }
        } catch (Exception e) {
            LOGGER.warn("failed to query source file for {} file: {}, error: {}", purpose, fileDB.getFileId(), e.getMessage());
        }

        return openAIFile;
    }

    private Progress transferToProgress(FileProgressDB fileProgressDB) {
        return Progress.builder()
                .id(fileProgressDB.getId())
                .fileId(fileProgressDB.getFileId())
                .name(fileProgressDB.getName())
                .status(fileProgressDB.getStatus())
                .message(fileProgressDB.getMessage())
                .percent(fileProgressDB.getPercent())
                .cuid(fileProgressDB.getCuid())
                .cuName(fileProgressDB.getCuName())
                .ctime(fileProgressDB.getCtime()
                        .toInstant(ZoneId.systemDefault().getRules().getOffset(fileProgressDB.getCtime()))
                        .toEpochMilli())
                .muid(fileProgressDB.getMuid())
                .muName(fileProgressDB.getMuName())
                .mtime(fileProgressDB.getMtime()
                        .toInstant(ZoneId.systemDefault().getRules().getOffset(fileProgressDB.getMtime()))
                        .toEpochMilli())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public OpenAIFile uploadWithUrl(File file, String type, String mimeType, String extension, String charset, String purpose, String metadata,
		boolean getUrl, long expires, String ancestorId, String filename, String description, String cities, String tags) {
        OpenAIFile openaiFile = upload(file, filename, purpose, metadata,
			mimeType, type, extension, charset, ancestorId, description, cities, tags);

        if(getUrl) {
            String url = getUrl(openaiFile.getId(), expires);
            openaiFile.setUrl(url);
        }
        return openaiFile;
    }

    public OpenAIFile upload(
            File file,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String charset) {
        return upload(file, filename, purpose, metadata, mimeType, type, extension, charset, null);
    }

	@Transactional(rollbackFor = Exception.class)
	public OpenAIFile upload(
		File file,
		String filename,
		String purpose,
		String metadata,
		String mimeType,
		String type,
		String extension,
		String charset,
		String ancestorId) {
		return upload(file, filename, purpose, metadata, mimeType, type, extension, charset, ancestorId, null, null, null);
	}

    @Transactional(rollbackFor = Exception.class)
    public OpenAIFile upload(
            File file,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String charset,
            String ancestorId,
			String description,
			String cities,
			String tags) {
        String spaceCode = BellaContextHelper.getOperateSpaceCode();
        FileType fileType = FilePurposeClassifier.classify(purpose);
        String fileId = FILE_ID_GENERATOR.generateWithType(fileType);
        String bucketName = purpose.equals(VISION) ? bucketConfig.getPublicBucket() : bucketConfig.getPrivateBucket();
        String keyName = String.format("%s/%s", purpose, fileId);
        if(StringUtils.isNotEmpty(extension)) {
            keyName += "." + extension;
        }
        storageService.putObject(bucketName, keyName, mimeType, file, filename, charset);

        String akCode = BellaContextHelper.getOperatorAkCode();

        // 保存文件信息到数据库
        FileDB fileDB = new FileDB();
        fileDB.setFileId(fileId);
        fileDB.setFilename(filename);
        fileDB.setExtension(extension);
        fileDB.setMimeType(mimeType);
        fileDB.setType(type);
        fileDB.setBucket(bucketName);
        fileDB.setPath(keyName);
        fileDB.setBytes(file.length());
        fileDB.setSpaceCode(spaceCode);
        fileDB.setPurpose(purpose);
        fileDB.setMetaData(metadata);
        fileDB.setAkCode(akCode);
		fileDB.setDescription(StringUtils.isNotEmpty(description) ? description : "");
		fileDB.setCities(StringUtils.isNotEmpty(cities) ? cities : "");
		fileDB.setTags(StringUtils.isNotEmpty(tags) ? tags : "");
        String shardingKey = fileRepo.addFile(fileDB, ancestorId, fileType);
        if(fileType.notUsersType()) {
            fileShardingCountUpdator.increase(shardingKey, fileType.getType());
        }

        FileDB res = fileRepo.queryFile(fileId, fileType);
        if(res == null) {
            throw new FileNotFoundException(fileId);
        }

        OpenAIFile openAIFile = buildOpenAIFileWithSource(res);

        // 文件创建后的广播机制
        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_CREATED);
        message.setData(openAIFile);
        message.setMetadata(metadata);
        message.setUserId(BellaContextHelper.getOperatorUserId());
        message.setUserName(BellaContextHelper.getOperatorUserName());
        broadcastService.broadcast(message, () -> updateBroadcastStatus(fileId, BroadcastStatus.SUCCESS),
                () -> updateBroadcastStatus(fileId, BroadcastStatus.FAILED));
        return openAIFile;
    }

    public List<OpenAIFile> list(
            String purpose,
            Integer limit,
            String order,
            String after,
            String ancestorId) {
        String spaceCode = BellaContextHelper.getOperateSpaceCode();
        List<FileDB> files = fileRepo.listFile(purpose, limit, order, after, spaceCode, ancestorId);
        List<OpenAIFile> emptyList = new ArrayList<>();
        // don't set source fIle for listing
        return files == null ? emptyList : files.stream().map(this::transferToOpenAIFile).collect(Collectors.toList());
    }

    public OpenAIFile getFile(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        FileDB fileDB = fileRepo.queryFile(fileId, fileType);
        if(fileDB == null) {
            throw new FileNotFoundException(fileId);
        }

        return transferToOpenAIFile(fileDB);
    }

    public FileDB getFile0(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        return fileRepo.queryFile(fileId, fileType);
    }

    public String updateRealFile(String fileId, String filename, File file, String mimeType, String charset) {
        FileType fileType = FileType.fromFileId(fileId);
        FileDB fileDB = fileRepo.queryFile(fileId, fileType);
        return storageService.putObject(fileDB.getBucket(), fileDB.getPath(), mimeType, file, filename, charset);
    }

    @Transactional(rollbackFor = Exception.class)
    public OpenAIFile updateFile(FileOps ops, boolean increaseVersion, Scope actionType) {
        FileType fileType = FileType.fromFileId(ops.getFileId());
        fileRepo.updateFile(ops, increaseVersion);
        FileDB fileDB = fileRepo.queryFile(ops.getFileId(), fileType);
        OpenAIFile finalOpenAIFile = buildOpenAIFileWithSource(fileDB);

        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_UPDATED);
        message.setScope(actionType.getValue());
        message.setData(finalOpenAIFile);
        message.setMetadata(fileDB.getMetaData());
        message.setUserId(BellaContextHelper.getOperatorUserId());
        message.setUserName(BellaContextHelper.getOperatorUserName());
        broadcastService.broadcast(message, () -> updateBroadcastStatus(finalOpenAIFile.getId(), BroadcastStatus.SUCCESS),
                () -> updateBroadcastStatus(finalOpenAIFile.getId(), BroadcastStatus.FAILED));

        return finalOpenAIFile;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        // 只标记status字段，不删除文件，不删除数据库记录
        FileOps op = new FileOps();
        op.setFileId(fileId);
        op.setStatus(FileStatus.DELETED);
        try {
            fileRepo.updateFile(op, false);
            if(fileType.needsDirectorySupport()) {
                fileRepo.deleteFileClosure(fileId, fileType);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update file status (indicating deletion), fileId: "
                    + fileId + ", status: " + FileStatus.DELETED, e);
        }
    }

    public String getUrl(String fileId) {
        return getUrl(fileId, ONE_DAY);
    }

    public String getUrl(
            String fileId,
            long expires) {
        FileDB file = fileRepo.queryFile(fileId);
        return getUrl(file.getBucket(), file.getPath(), file.getPurpose(), expires);
    }

    public String getUrl(String bucketName, String keyName, String purpose, long expires) {
        return purpose.equals(VISION) ? storageService.getPublicUrl(bucketName, keyName)
                : storageService.getPresignedUrl(bucketName, keyName, expires);
    }

    public void updateProgress(
            UpdateProgressRequestData data,
            String fileId,
            String progressName) {
        String status = data.getStatus();
        String message = data.getMessage();
        Integer percent = data.getPercent();
        if(fileRepo.queryProgress(fileId, progressName) == null) {
            fileRepo.insertProgress(fileId, progressName, status, message, percent);
        } else {
            fileRepo.updateProgress(fileId, progressName, status, message, percent);
        }
    }

    public Progress getProgress(
            String fileId,
            String progressName) {
        FileProgressDB fileProgressDB = fileRepo.queryProgress(fileId, progressName);
        return fileProgressDB == null ? null : transferToProgress(fileProgressDB);
    }

    /**
     * 支持通过fileIds批量查询，默认所有file在同一个space下
     *
     * @param ops
     *
     * @return
     */
    public List<OpenAIFile> getFiles(ListFileOps ops) {
        List<FileDB> files = fileRepo.getFiles(ops);
        List<OpenAIFile> emptyList = new ArrayList<>();
        // don't set source file for listing
        return files == null ? emptyList : files.stream().map(this::transferToOpenAIFile).collect(Collectors.toList());
    }

    public String getPreviewUrl(String fileId, Long expires) {
        FileDB file = fileRepo.queryFile(fileId);
        String bucketName = file.getBucket();
        String keyName = file.getPath();
        return storageService.getPreviewUrl(bucketName, keyName, expires);
    }

    public InputStreamWithCharset getFileInputStream(String fileId) {
        try {
            // 获取文件信息
            FileDB file = fileRepo.queryFile(fileId);
            if(file == null) {
                LOGGER.warn("file not found, file_id = {}", fileId);
                return null;
            }

            String bucketName = file.getBucket();
            String keyName = file.getPath();

            return storageService.getObjectInputStream(bucketName, keyName);
        } catch (Exception e) {
            String errMsg = String.format("failed to get input stream for file: %s, error: %s", fileId, e.getMessage());
            LOGGER.error(errMsg, e);
            throw new IllegalStateException(errMsg, e);
        }
    }

	public OpenAIFile mkdir(String name, String ancestorId, String description) {
        String spaceCode = BellaContextHelper.getOperateSpaceCode();

        FileType fileType = FileType.DIRECTORY;
        String fileId = FILE_ID_GENERATOR.generateDirId();
        String akCode = BellaContextHelper.getOperatorAkCode();

        FileDB fileDB = new FileDB();
        fileDB.setFileId(fileId);
        fileDB.setFilename(name);
        fileDB.setExtension("");
        fileDB.setMimeType("");
        fileDB.setType("");
        fileDB.setBucket("");
        fileDB.setPath("");
        fileDB.setBytes(0L);
        fileDB.setSpaceCode(spaceCode);
        fileDB.setPurpose(null);
        fileDB.setMetaData("{}");
        fileDB.setAkCode(akCode);
        fileDB.setIsDir(1);
		fileDB.setDescription(description == null ? "" : description);

        fileRepo.addFile(fileDB, ancestorId, fileType);

        FileDB res = fileRepo.queryFile(fileId, fileType);
        if(res == null) {
            throw new FileNotFoundException(fileId);
        }

        OpenAIFile openAIFile = transferToOpenAIFile(res);

        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_CREATED);
        message.setData(openAIFile);
        message.setMetadata("{}");
        message.setUserId(BellaContextHelper.getOperatorUserId());
        message.setUserName(BellaContextHelper.getOperatorUserName());
        broadcastService.broadcast(message, () -> updateBroadcastStatus(fileId, BroadcastStatus.SUCCESS),
                () -> updateBroadcastStatus(fileId, BroadcastStatus.FAILED));
        return openAIFile;
    }

    public List<OpenAIFile> findFiles(FileDB ancestor) {
        List<FileDB> fileDbs = fileRepo.findFiles(ancestor.getSpaceCode(), ancestor.getFileId());
        return fileDbs.stream()
                .map(this::transferToOpenAIFile)
                .collect(Collectors.toList());
    }

    public List<OpenAIFile> findFiles(String spaceCode) {
        List<FileDB> fileDbs = fileRepo.findFiles(spaceCode, null);
        return fileDbs.stream()
                .map(this::transferToOpenAIFile)
                .collect(Collectors.toList());
    }

    public OpenAIFile info(String fileId) {
        List<FileDB> pathFiles = fileRepo.getPathFiles(fileId);
        StringBuilder pathBuilder = new StringBuilder();
        for (FileDB file : pathFiles) {
            pathBuilder.append("/");
            pathBuilder.append(file.getFilename());
        }

        OpenAIFile result = transferToOpenAIFile(pathFiles.get(0));

        return result.toBuilder().path(pathBuilder.toString()).build();
    }

    public String getDirectAncestorId(String fileId) {
        return fileRepo.getDirectAncestorId(fileId);
    }

    /**
     * 初始化文件分片管理定时任务
     */
    @PostConstruct
    public void init() {
        // 每5秒刷新文件分片计数到数据库
        TaskExecutor.scheduleAtFixedRate(() -> fileShardingCountUpdator.flush(), 5);
        // 每60秒检查是否需要创建新分片
        TaskExecutor.scheduleAtFixedRate(() -> fileShardingCountUpdator.trySharding(), 60);
    }

	public Page<OpenAIFile> pageFiles(PageFileOps ops) {
		Page<FileDB> pageResult = fileRepo.pageFiles(ops);
		List<OpenAIFile> openAIFiles = pageResult.getRecords().stream()
			.map(this::transferToOpenAIFile)
			.collect(Collectors.toList());

		if(ops.isGetUrl()) {
			long expires = ops.getExpires() > 0 ? ops.getExpires() : ONE_DAY;
			for (OpenAIFile file : openAIFiles) {
				String url = getUrl(file.getId(), expires);
				file.setUrl(url);
			}
		}

		return Page.<OpenAIFile>builder()
			.pageNo(pageResult.getPageNo())
			.pageSize(pageResult.getPageSize())
			.total(pageResult.getTotal())
			.pages(pageResult.getPages())
			.records(openAIFiles)
			.build();
	}

	public int count(String ancestorId, String type) {
		return fileRepo.countFiles(ancestorId, type);
	}

	public List<FileCountInfo> batchCount(List<String> ancestorIds, String type, String spaceCode) {
		return fileRepo.batchCountFiles(ancestorIds, type, spaceCode);
	}

	/**
	 * 移动文件/目录到指定位置，包含完整的业务校验和事务处理
	 *
	 * @param fileIds            要移动的文件/目录ID列表
	 * @param targetAncestorId   目标父目录ID，为null表示移动到根目录
	 * @param effectiveSpaceCode 有效的空间编码
	 *
	 * @return 移动后的文件/目录列表
	 */
	@Transactional(rollbackFor = Exception.class)
	public List<OpenAIFile> moveFiles(List<String> fileIds, String targetAncestorId, String effectiveSpaceCode) {
		// 验证目标目录
		validateTargetDirectory(targetAncestorId);

		// 验证要移动的文件
		validateFilesToMove(fileIds, targetAncestorId, effectiveSpaceCode);

		// 执行移动操作
		List<OpenAIFile> movedFiles = new ArrayList<>();
		for (String fileId : fileIds) {
			fileRepo.moveSubtree(fileId, targetAncestorId);
			movedFiles.add(getFile(fileId));
		}

		return movedFiles;
	}

	/**
	 * 验证目标目录的有效性
	 */
	private void validateTargetDirectory(String targetAncestorId) {
		if(targetAncestorId != null) {
			OpenAIFile targetDir = getFile(targetAncestorId);
			Assert.notNull(targetDir, "Target directory not found: " + targetAncestorId);
			Assert.isTrue(targetDir.getIsDir(), "Target must be a directory: " + targetAncestorId);
		}
	}

	/**
	 * 验证要移动的文件列表
	 */
	private void validateFilesToMove(List<String> fileIds, String targetAncestorId, String spaceCode) {
		// 批量查询所有要移动的文件信息
		ListFileOps ops = new ListFileOps();
		ops.setFileIds(fileIds);
		List<OpenAIFile> queriedFiles = getFiles(ops);

		// 验证查询结果
		if(queriedFiles.size() != fileIds.size()) {
			// 找出未找到的文件ID
			List<String> foundIds = queriedFiles.stream().map(OpenAIFile::getId).collect(Collectors.toList());
			List<String> notFoundIds = fileIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
			throw new IllegalArgumentException("Files not found: " + notFoundIds);
		}

		// 验证空间编码
		for (OpenAIFile file : queriedFiles) {
			if(!StringUtils.equals(spaceCode, file.getSpaceCode())) {
				throw new IllegalArgumentException("space mismatch for file: " + file.getId());
			}
		}

		// 校验防环 - 不能将节点移动到自身或其后代下面
		validateNoCycles(queriedFiles, targetAncestorId);

		// 校验同级重名 - 目标位置下不能存在重名文件/目录
		validateNoDuplicateNames(queriedFiles, targetAncestorId, spaceCode);
	}

	/**
	 * 校验防环：不能将目录移动到自身或其后代节点
	 */
	private void validateNoCycles(List<OpenAIFile> filesToMove, String targetAncestorId) {
		if(targetAncestorId == null) {
			return; // 移动到根目录不会有环
		}

		// 检查是否有文件要移动到自身
		for (OpenAIFile file : filesToMove) {
			Assert.isTrue(!file.getId().equals(targetAncestorId),
				String.format("Cannot move file to itself: %s", file.getId()));
		}

		// 批量检查目录是否移动到其后代节点
		// 正确逻辑：检查目标位置是否是要移动的目录的后代
		List<String> dirIds = filesToMove.stream()
			.filter(OpenAIFile::getIsDir)
			.map(OpenAIFile::getId)
			.collect(Collectors.toList());

		if(!dirIds.isEmpty()) {
			// 检查targetAncestorId是否是dirIds中任一目录的后代
			List<String> ancestorIds = fileRepo.batchCheckTargetAsDescendant(targetAncestorId, dirIds);
			for (String ancestorId : ancestorIds) {
				String filename = filesToMove.stream()
					.filter(f -> f.getId().equals(ancestorId))
					.findFirst()
					.map(OpenAIFile::getFilename)
					.orElse(ancestorId);
				throw new IllegalArgumentException(
					String.format("Cannot move directory '%s' to its descendant '%s'",
						filename, targetAncestorId));
			}
		}
	}

	/**
	 * 校验同级重名：目标目录下不能存在同名文件/目录（批量优化版本）
	 */
	private void validateNoDuplicateNames(List<OpenAIFile> filesToMove, String targetAncestorId, String spaceCode) {
		if(filesToMove.isEmpty()) {
			return;
		}

		// 1. 批量获取所有文件的当前父目录ID
		List<String> fileIds = filesToMove.stream().map(OpenAIFile::getId).collect(Collectors.toList());
		Map<String, String> fileToAncestorMap = fileRepo.batchGetDirectAncestorIds(spaceCode, fileIds);

		// 2. 过滤出需要校验的文件（不在目标目录的文件）
		List<String> filenamesToCheck = filesToMove.stream()
			.filter(file -> !StringUtils.equals(fileToAncestorMap.get(file.getId()), targetAncestorId))
			.map(OpenAIFile::getFilename)
			.collect(Collectors.toList());

		if(filenamesToCheck.isEmpty()) {
			return;
		}

		// 3. 批量检查目标目录下是否存在同名文件
		List<String> existingFilenames = fileRepo.batchCheckExistingFilenames(spaceCode, targetAncestorId, filenamesToCheck);

		if(!existingFilenames.isEmpty()) {
			String location = targetAncestorId == null ? "root directory" : "target directory";
			throw new IllegalArgumentException(
				String.format("Files already exist in %s: %s", location, String.join(", ", existingFilenames)));
		}
	}

	/**
	 * 确定有效的空间编码和祖先ID 优先级：如果ancestorId不为空，从ancestorId获取spaceCode；否则使用提供的spaceCode
	 */
	public EffectiveParams determineEffectiveParams(String spaceCode, String ancestorId) {
		if(StringUtils.isNotEmpty(ancestorId)) {
			OpenAIFile ancestorFile = getFile(ancestorId);
			if(ancestorFile == null) {
				throw new IllegalArgumentException("ancestor_id not found: " + ancestorId);
			}
			return new EffectiveParams(ancestorFile.getSpaceCode(), ancestorId);
		} else {
			return new EffectiveParams(spaceCode, null);
		}
	}

	@Data
	@AllArgsConstructor
	public static class EffectiveParams {
		private String spaceCode;
		private String ancestorId;
	}

	@Data
	@AllArgsConstructor
    @Builder
    public static class InputStreamWithCharset {
        private java.io.InputStream inputStream;
        private String charset;
    }
}
