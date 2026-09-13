package com.fishbook.common.error;

import com.fishbook.administration.application.InvalidAdminFishQueryException;
import com.fishbook.administration.photos.application.InvalidAdminPhotoQueryException;
import com.fishbook.catalog.application.InvalidCatalogQueryException;
import com.fishbook.catalog.domain.FishNotFoundException;
import com.fishbook.catalog.domain.InvalidFishSpeciesException;
import com.fishbook.catalog.domain.InvalidPublicationTransitionException;
import com.fishbook.catchlog.application.InvalidCatchRecordQueryException;
import com.fishbook.catchlog.application.CatchPhotoNotFoundException;
import com.fishbook.catchlog.application.CatchPhotoConflictException;
import com.fishbook.catchlog.application.InvalidPhotoVersionException;
import com.fishbook.catchlog.application.PhotoVersionRequiredException;
import com.fishbook.catchlog.domain.CatchRecordNotFoundException;
import com.fishbook.catchlog.domain.InvalidCatchRecordException;
import com.fishbook.favorites.application.InvalidFavoriteQueryException;
import com.fishbook.identity.domain.DuplicateEmailException;
import com.fishbook.identity.domain.InvalidEmailException;
import com.fishbook.identity.domain.InvalidNicknameException;
import com.fishbook.identity.domain.InvalidPasswordException;
import com.fishbook.identity.domain.UserNotFoundException;
import com.fishbook.media.application.InvalidCatchPhotoException;
import com.fishbook.media.domain.MediaStorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(PhotoVersionRequiredException.class)
    ResponseEntity<ApiErrorResponse> handlePhotoVersionRequired(HttpServletRequest request) {
        return error(HttpStatus.PRECONDITION_REQUIRED, "PHOTO_VERSION_REQUIRED", "请提供当前照片版本", List.of(), request);
    }

    @ExceptionHandler(InvalidPhotoVersionException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidPhotoVersion(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PHOTO_VERSION", "照片版本无效", List.of(), request);
    }

    @ExceptionHandler(InvalidAdminPhotoQueryException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidAdminPhotoQuery(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_PHOTO_QUERY", "照片管理查询无效", List.of(), request);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({CatchPhotoConflictException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ApiErrorResponse> handleCatchPhotoConflict(
            RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CATCH_PHOTO_CONFLICT",
                "照片或记录已被修改，请刷新后重新确认操作", List.of(), request);
    }

    @ExceptionHandler(InvalidCatalogQueryException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCatalogQuery(
            InvalidCatalogQueryException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                exception.code(),
                "Catalog query is invalid",
                List.of(),
                request);
    }

    @ExceptionHandler(InvalidAdminFishQueryException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidAdminFishQuery(
            InvalidAdminFishQueryException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_CATALOG_QUERY",
                "Catalog query is invalid",
                List.of(),
                request);
    }

    @ExceptionHandler(InvalidPublicationTransitionException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidPublicationTransition(
            InvalidPublicationTransitionException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                exception.code(),
                "Fish publication transition is invalid",
                List.of(),
                request);
    }

    @ExceptionHandler(InvalidFishSpeciesException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidFishSpecies(
            InvalidFishSpeciesException exception,
            HttpServletRequest request) {
        return invalidFishContent(exception.field(), request);
    }

    @ExceptionHandler(InvalidFavoriteQueryException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidFavoriteQuery(
            InvalidFavoriteQueryException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                exception.code(),
                "Favorite query is invalid",
                List.of(),
                request);
    }

    @ExceptionHandler({InvalidCatchRecordException.class, InvalidCatchRecordQueryException.class})
    ResponseEntity<ApiErrorResponse> handleInvalidCatchRecord(
            RuntimeException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_CATCH_RECORD",
                "Catch record is invalid",
                List.of(),
                request);
    }

    @ExceptionHandler(CatchRecordNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleCatchRecordNotFound(
            CatchRecordNotFoundException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                exception.code(),
                "Catch record was not found",
                List.of(),
                request);
    }

    @ExceptionHandler(CatchPhotoNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleCatchPhotoNotFound(
            CatchPhotoNotFoundException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                exception.code(),
                "Catch photo was not found",
                List.of(),
                request);
    }

    @ExceptionHandler({
            InvalidCatchPhotoException.class,
            MaxUploadSizeExceededException.class,
            MissingServletRequestPartException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidCatchPhoto(
            Exception exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_CATCH_PHOTO",
                "Catch photo is invalid",
                List.of(),
                request);
    }

    @ExceptionHandler(MediaStorageUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleMediaStorageUnavailable(
            MediaStorageUnavailableException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "MEDIA_STORAGE_UNAVAILABLE",
                "媒体存储暂时不可用",
                List.of(),
                request);
    }

    @ExceptionHandler(FishNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleFishNotFound(
            FishNotFoundException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                exception.code(),
                "Fish was not found",
                List.of(),
                request);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateEmail(
            DuplicateEmailException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                exception.code(),
                "An account with that email already exists",
                List.of(),
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateConstraint(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        if (causedByDuplicateEmailConstraint(exception)) {
            return error(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_EMAIL",
                    "An account with that email already exists",
                    List.of(),
                    request);
        }
        if (causedByAliasUniqueConstraint(exception)) {
            return invalidFishContent("aliases", request);
        }
        if (causedByCatalogUniqueConstraint(exception)) {
            return error(
                    HttpStatus.CONFLICT,
                    "CATALOG_ENTRY_CONFLICT",
                    "A fish catalog entry already exists",
                    List.of(),
                    request);
        }
        return handleUnexpected(exception, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldErrorItem> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(fieldError -> new FieldErrorItem(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()))
                .toList();
        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                fieldErrors,
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldErrorItem> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorItem(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .sorted(Comparator.comparing(FieldErrorItem::field))
                .toList();
        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                fieldErrors,
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Request body is invalid",
                List.of(),
                request);
    }

    @ExceptionHandler({
            InvalidEmailException.class,
            InvalidPasswordException.class,
            InvalidNicknameException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidIdentityInput(
            RuntimeException exception,
            HttpServletRequest request) {
        String code = switch (exception) {
            case InvalidEmailException invalidEmail -> invalidEmail.code();
            case InvalidPasswordException invalidPassword -> invalidPassword.code();
            case InvalidNicknameException invalidNickname -> invalidNickname.code();
            default -> throw new IllegalStateException("Unsupported identity validation exception");
        };
        return error(
                HttpStatus.BAD_REQUEST,
                code,
                exception.getMessage(),
                List.of(),
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleAuthenticationFailure(
            AuthenticationException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Email or password is incorrect",
                List.of(),
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access is denied",
                List.of(),
                request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleMissingUser(
            UserNotFoundException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND",
                "User was not found",
                List.of(),
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> handleMissingResource(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/api/v1/admin".equals(path) || path.startsWith("/api/v1/admin/")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return handleUnexpected(exception, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        String requestId = requestId(request);
        LOGGER.error("Unexpected request failure; requestId={}", requestId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                List.of(),
                requestId));
    }

    private static boolean causedByDuplicateEmailConstraint(Throwable exception) {
        return causedByConstraint(exception, "uk_users_email");
    }

    private static boolean causedByCatalogUniqueConstraint(Throwable exception) {
        return causedByConstraint(
                exception,
                "uk_fish_species_slug",
                "uk_fish_species_common_name_zh",
                "uk_fish_species_scientific_name");
    }

    private static boolean causedByAliasUniqueConstraint(Throwable exception) {
        return causedByConstraint(exception, "uk_fish_aliases_species_alias");
    }

    private static ResponseEntity<ApiErrorResponse> invalidFishContent(
            String field,
            HttpServletRequest request) {
        List<FieldErrorItem> fieldErrors = "aliases".equals(field)
                ? List.of(new FieldErrorItem("aliases", "Aliases must be unique"))
                : List.of();
        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Fish content is invalid",
                fieldErrors,
                request);
    }

    private static boolean causedByConstraint(Throwable exception, String... constraintNames) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null) {
                for (String constraintName : constraintNames) {
                    if (current.getMessage().contains(constraintName)) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            List<FieldErrorItem> fieldErrors,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code,
                message,
                fieldErrors,
                requestId(request)));
    }

    public static String requestId(HttpServletRequest request) {
        String requestId = request.getRequestId();
        return requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
    }
}
