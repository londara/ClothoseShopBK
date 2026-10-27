package com.aurastudio.backend.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ErrorCode;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.config.AuraProperties;
import com.aurastudio.backend.tenant.ShopContext;

/**
 * Image uploads (docs/API_AND_DATABASE.md §10). Files live on disk under
 * {@code aura.uploads.dir/shops/{shopId}/{file}} and each has an {@code uploads}
 * row with the shop id, so products/settings only accept their own shop's files.
 */
@Service
public class UploadService {

	private static final Map<String, String> TYPES = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp",
			"webp", "image/gif", "gif", "image/avif", "avif");
	private static final Map<String, String> CONTENT_TYPES = Map.of("jpg", "image/jpeg", "png", "image/png", "webp",
			"image/webp", "gif", "image/gif", "avif", "image/avif");
	public static final Pattern FILE_NAME = Pattern.compile("^[a-z0-9-]+\\.(jpg|png|webp|gif|avif)$");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final JdbcClient jdbc;
	private final Path root;
	private final long maxBytes;

	public UploadService(JdbcClient jdbc, AuraProperties properties) {
		this.jdbc = jdbc;
		this.root = Path.of(properties.uploads().dir()).toAbsolutePath().normalize();
		this.maxBytes = properties.uploads().maxBytes();
	}

	public record Uploaded(UUID id, String url, String mimeType, long sizeBytes) {
	}

	public record StoredFile(Path path, String contentType) {
	}

	public Uploaded store(ShopContext ctx, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(ErrorCode.UNSUPPORTED_FILE, Messages.FILE_TYPE, "file", null);
		}
		if (file.getSize() > maxBytes) {
			throw new ApiException(ErrorCode.FILE_TOO_LARGE, Messages.FILE_TOO_LARGE, "file", null);
		}
		// The browser's Content-Type is just a claim: the type comes from the file's own bytes,
		// so an HTML/SVG/script file renamed to .png is refused.
		String mime = detectImageType(file);
		String ext = mime == null ? null : TYPES.get(mime);
		if (ext == null) {
			throw new ApiException(ErrorCode.UNSUPPORTED_FILE, Messages.FILE_TYPE, "file", null);
		}
		long count = jdbc.sql("SELECT count(*) FROM uploads WHERE shop_id = :shopId").param("shopId", ctx.shopId())
				.query(Long.class).single();
		if (count >= MAX_FILES_PER_SHOP) {
			throw ApiException.validation("file", Messages.TOO_MANY_ITEMS);
		}
		byte[] random = new byte[6];
		RANDOM.nextBytes(random);
		String fileName = Long.toString(System.currentTimeMillis(), 36) + "-" + HexFormat.of().formatHex(random) + "."
				+ ext;
		String storageKey = "shops/" + ctx.shopId() + "/" + fileName;
		Path target = root.resolve(storageKey).normalize();
		try (InputStream in = file.getInputStream()) {
			Files.createDirectories(target.getParent());
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not store upload", e);
		}
		UUID id = jdbc.sql("""
				INSERT INTO uploads (file_name, mime_type, size_bytes, shop_id, storage_key, uploaded_by)
				VALUES (:name, :mime, :size, :shopId, :key, :userId) RETURNING id""")
				.param("name", fileName).param("mime", mime).param("size", (int) file.getSize())
				.param("shopId", ctx.shopId()).param("key", storageKey).param("userId", ctx.userId())
				.query(UUID.class).single();
		return new Uploaded(id, "/uploads/" + fileName, mime, file.getSize());
	}

	/** Disk quota per shop (each file ≤ 5 MB). */
	private static final int MAX_FILES_PER_SHOP = 5_000;

	/** JPEG / PNG / GIF / WEBP / AVIF by their magic numbers, or null. */
	static String detectImageType(MultipartFile file) {
		byte[] h = new byte[16];
		int n;
		try (InputStream in = file.getInputStream()) {
			n = in.readNBytes(h, 0, h.length);
		}
		catch (IOException e) {
			return null;
		}
		if (n >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		if (n >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G' && h[4] == '\r'
				&& h[5] == '\n' && h[6] == 0x1A && h[7] == '\n') {
			return "image/png";
		}
		if (n >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8' && (h[4] == '7' || h[4] == '9')
				&& h[5] == 'a') {
			return "image/gif";
		}
		if (n >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E'
				&& h[10] == 'B' && h[11] == 'P') {
			return "image/webp";
		}
		if (n >= 12 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p' && h[8] == 'a' && h[9] == 'v'
				&& h[10] == 'i' && (h[11] == 'f' || h[11] == 's')) {
			return "image/avif";
		}
		return null;
	}

	/** True when the file was uploaded by THIS shop (rule 6 of §4.1). */
	public boolean belongsToShop(UUID shopId, String fileName) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM uploads WHERE file_name = :name AND shop_id = :shopId)")
				.param("name", fileName).param("shopId", shopId).query(Boolean.class).single();
	}

	/** Public read by file name (names are random and unique). */
	public Optional<StoredFile> find(String fileName) {
		if (!FILE_NAME.matcher(fileName).matches()) {
			return Optional.empty();
		}
		return jdbc.sql("SELECT storage_key FROM uploads WHERE file_name = :name").param("name", fileName)
				.query(String.class).optional()
				.map(key -> root.resolve(key).normalize())
				.filter(path -> path.startsWith(root) && Files.isRegularFile(path))
				.map(path -> new StoredFile(path, CONTENT_TYPES.get(fileName.substring(fileName.lastIndexOf('.') + 1))));
	}
}
