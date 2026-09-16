import base64
from io import BytesIO
from unittest.mock import Mock

import pytest
from PIL import Image

from app.core.config import Settings
from app.domain.contracts import ImageRef
from app.infrastructure.equipment_images import EquipmentImages


@pytest.fixture
def image_loader(monkeypatch):
    data = BytesIO()
    Image.new("RGB", (1000, 500)).save(data, format="PNG")
    raw = data.getvalue()
    body = Mock()
    body.read.return_value = raw
    s3 = Mock()
    s3.get_object.return_value = {
        "Body": body,
        "ContentType": "image/png",
        "ContentLength": len(raw),
    }
    monkeypatch.setattr("app.infrastructure.equipment_images.boto3.client", Mock(return_value=s3))
    loader = EquipmentImages(Settings(_env_file=None, s3_bucket="test-bucket"))
    ref = ImageRef(
        image_id="1",
        object_key="equipment/temp/1/test.png",
        etag='"version"',
        capture_slot="OVERVIEW",
        content_type="image/png",
        size_bytes=len(raw),
    )
    return loader, s3, body, ref


def test_image_is_version_pinned_and_resized(image_loader):
    loader, s3, body, ref = image_loader
    value = loader.load(ref)
    s3.get_object.assert_called_once_with(
        Bucket="test-bucket", Key=ref.object_key, IfMatch='"version"'
    )
    with Image.open(BytesIO(base64.b64decode(value.split(",", 1)[1]))) as image:
        assert image.size == (768, 384)
        assert image.format == "JPEG"
        assert not image.getexif()
    body.close.assert_called_once()


def test_equipment_identification_can_keep_more_label_detail(image_loader):
    loader, _, _, ref = image_loader
    loader.max_dimension = 1536
    value = loader.load(ref)
    with Image.open(BytesIO(base64.b64decode(value.split(",", 1)[1]))) as image:
        # 원본이 상한보다 작으면 모델 번호를 읽기 위해 불필요하게 확대하지 않는다.
        assert image.size == (1000, 500)


def test_named_aws_profile_is_used_without_copying_credentials(image_loader, monkeypatch):
    _, s3, _, ref = image_loader
    session = Mock()
    session.client.return_value = s3
    session_factory = Mock(return_value=session)
    monkeypatch.setattr("app.infrastructure.equipment_images.boto3.Session", session_factory)
    loader = EquipmentImages(
        Settings(_env_file=None, s3_bucket="test-bucket", s3_profile="iter-local")
    )
    loader.load(ref)
    session_factory.assert_called_once_with(profile_name="iter-local")
    session.client.assert_called_once()


def test_non_temporary_object_is_rejected_before_read(image_loader):
    loader, s3, _, ref = image_loader
    ref.object_key = "equipment/public/other.jpg"
    with pytest.raises(ValueError):
        loader.load(ref)
    s3.get_object.assert_not_called()


def test_version_is_required(image_loader):
    loader, s3, _, ref = image_loader
    ref.etag = None
    with pytest.raises(ValueError):
        loader.load(ref)
    s3.get_object.assert_not_called()


def test_condition_prefix_does_not_allow_other_public_images(image_loader):
    _, s3, _, ref = image_loader
    loader = EquipmentImages(
        Settings(_env_file=None, s3_bucket="test-bucket"),
        prefix="equipment/public/rental-evidence/",
    )
    ref.object_key = "equipment/public/other.jpg"
    with pytest.raises(ValueError):
        loader.load(ref)
    s3.get_object.assert_not_called()
    ref.object_key = "equipment/public/rental-evidence/before.png"
    loader.load(ref)
    assert s3.get_object.call_args.kwargs["IfMatch"] == ref.etag


def test_metadata_mismatch_closes_body(image_loader):
    loader, _, body, ref = image_loader
    ref.size_bytes += 1
    with pytest.raises(ValueError):
        loader.load(ref)
    body.close.assert_called_once()


def test_content_hash_mismatch_is_rejected(image_loader):
    loader, _, _, ref = image_loader
    ref.sha256 = "0" * 64
    with pytest.raises(ValueError):
        loader.load(ref)
