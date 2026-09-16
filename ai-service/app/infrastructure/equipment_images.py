import base64
import hashlib
from io import BytesIO

import boto3
from botocore.config import Config
from PIL import Image, ImageOps

from app.core.config import Settings
from app.domain.contracts import ImageRef


class EquipmentImages:
    # 설정된 bucket과 허용 prefix만 사용하는 제한된 S3 client를 준비한다.
    def __init__(
        self,
        settings: Settings,
        *,
        prefix: str | tuple[str, ...] | None = None,
        max_dimension: int = 768,
    ) -> None:
        allowed_prefixes = (settings.s3_key_prefix,) if prefix is None else prefix
        if isinstance(allowed_prefixes, str):
            allowed_prefixes = (allowed_prefixes,)

        if not settings.s3_bucket or not all(value.strip("/") for value in allowed_prefixes):
            raise ValueError("S3 image settings are required")

        self.bucket = settings.s3_bucket
        self.prefixes = tuple(value.rstrip("/") + "/" for value in allowed_prefixes)
        self.max_dimension = max_dimension

        client_factory = (
            boto3.Session(profile_name=settings.s3_profile).client
            if settings.s3_profile
            else boto3.client
        )

        self.client = client_factory(
            "s3",
            region_name=settings.s3_region,
            config=Config(connect_timeout=3, read_timeout=10, retries={"total_max_attempts": 1}),
        )

    # 허용된 S3 객체의 버전·형식·크기를 검증하고 메타데이터를 제거해 AI 입력으로 만든다.
    def load(self, reference: ImageRef) -> str:
        original_bytes = self._download(reference)
        safe_image_bytes = self._prepare_for_ai(original_bytes, reference.content_type)
        encoded = base64.b64encode(safe_image_bytes).decode("ascii")

        return "data:image/jpeg;base64," + encoded

    # 허용 경로와 고정된 객체 버전을 확인한 뒤 크기 제한 안에서 S3 사진을 읽는다.
    def _download(self, reference: ImageRef) -> bytes:
        if not reference.object_key.startswith(self.prefixes):
            raise ValueError("Image is outside the allowed prefix")

        if not reference.etag and not reference.sha256:
            raise ValueError("Image version must be pinned")

        args = {"Bucket": self.bucket, "Key": reference.object_key}

        if reference.etag:
            # 요청 생성 시 확인한 ETag와 현재 객체가 다르면 S3가 다운로드를 거절한다.
            args["IfMatch"] = reference.etag

        response = self.client.get_object(**args)
        body = response["Body"]

        try:
            same_size = response["ContentLength"] == reference.size_bytes
            same_content_type = response["ContentType"] == reference.content_type

            if not same_size or not same_content_type:
                raise ValueError("Image metadata mismatch")

            # 선언된 크기보다 1 byte 더 읽어 실제 파일이 더 큰 경우도 탐지한다.
            data = body.read(reference.size_bytes + 1)
        finally:
            body.close()

        if len(data) != reference.size_bytes:
            raise ValueError("Image size mismatch")

        if reference.sha256:
            actual_hash = hashlib.sha256(data).hexdigest()
            expected_hash = reference.sha256.removeprefix("sha256:").lower()

            if actual_hash != expected_hash:
                raise ValueError("Image hash mismatch")

        return data

    # 이미지 형식을 실제 내용으로 확인하고 크기를 줄인 새 JPEG로 개인정보 메타데이터를 제거한다.
    def _prepare_for_ai(self, data: bytes, expected_content_type: str) -> bytes:
        supported_formats = {"JPEG": "image/jpeg", "PNG": "image/png", "WEBP": "image/webp"}

        with Image.open(BytesIO(data)) as original:
            too_many_pixels = original.width * original.height > 20_000_000
            unsupported_format = original.format not in supported_formats
            animated_image = getattr(original, "is_animated", False)

            if too_many_pixels or unsupported_format or animated_image:
                raise ValueError("Unsupported image")

            if supported_formats[original.format] != expected_content_type:
                raise ValueError("Image format mismatch")

            # EXIF 회전만 픽셀에 적용한 뒤 새 JPEG로 저장해 나머지 EXIF/GPS 정보는 버린다.
            picture = ImageOps.exif_transpose(original).convert("RGB")
            picture.thumbnail((self.max_dimension, self.max_dimension))

            output = BytesIO()
            picture.save(output, format="JPEG", quality=80)

            return output.getvalue()
