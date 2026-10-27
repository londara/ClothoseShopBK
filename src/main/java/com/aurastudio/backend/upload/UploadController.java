package com.aurastudio.backend.upload;

import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.config.OpenApiConfig;
import com.aurastudio.backend.tenant.CurrentShop;
import com.aurastudio.backend.tenant.ShopContext;
import com.aurastudio.backend.upload.UploadService.Uploaded;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "6. Uploads")
public class UploadController {

	private final UploadService uploads;

	public UploadController(UploadService uploads) {
		this.uploads = uploads;
	}

	@PostMapping(path = "/api/v1/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Upload an image (JPG/PNG/WEBP/GIF/AVIF, max 5 MB)",
			description = "Stored for the signed-in user's shop. Use the returned url in products or settings.",
			security = @SecurityRequirement(name = OpenApiConfig.BEARER))
	public ApiResponse<Uploaded> upload(@CurrentShop ShopContext ctx, @RequestPart("file") MultipartFile file) {
		return ApiResponse.of(uploads.store(ctx, file));
	}

	@GetMapping("/uploads/{fileName}")
	@Operation(summary = "Serve an uploaded image (public, cached 1 year)")
	public ResponseEntity<Resource> serve(@PathVariable String fileName) {
		var file = uploads.find(fileName).orElseThrow(() -> ApiException.notFound(Messages.NOT_FOUND));
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
				// Defence in depth: even if a file were not an image, the browser must not
				// sniff it into HTML or run anything from it when opened directly.
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Security-Policy", "default-src 'none'; img-src 'self'; sandbox")
				.header("Content-Disposition", "inline; filename=\"" + fileName + "\"")
				.contentType(MediaType.parseMediaType(file.contentType()))
				.body(new FileSystemResource(file.path()));
	}
}
