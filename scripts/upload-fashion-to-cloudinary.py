from pathlib import Path
import csv
import os
import uuid
from urllib.parse import unquote, urlparse

import cloudinary
import cloudinary.uploader
from dotenv import load_dotenv


REPOSITORY = Path(__file__).resolve().parents[1]
SELECTED_ROOT = (
    REPOSITORY / "commerce-service" / ".seed-data" / "fashion-products" / "selected"
)
INPUT_FILE = SELECTED_ROOT / "selected-catalog.csv"
OUTPUT_FILE = SELECTED_ROOT / "cloudinary-catalog.csv"
IMAGES_ROOT = SELECTED_ROOT / "images"
UUID_NAMESPACE = uuid.UUID("f348d1a7-6abf-49d0-b5cd-8d83d26b4145")


def stable_uuid(kind: str, dataset_id: str) -> uuid.UUID:
    return uuid.uuid5(UUID_NAMESPACE, f"stylemind:myntra:{kind}:{dataset_id}")


def existing_dataset_ids() -> set[str]:
    if not OUTPUT_FILE.exists():
        return set()
    with OUTPUT_FILE.open("r", encoding="utf-8-sig", newline="") as file:
        return {
            row["dataset_id"]
            for row in csv.DictReader(file)
            if row.get("dataset_id")
        }


def main() -> None:
    load_dotenv(REPOSITORY / ".env")
    cloudinary_url = os.getenv("CLOUDINARY_URL")
    if not cloudinary_url:
        raise RuntimeError("CLOUDINARY_URL is missing from .env")
    if not INPUT_FILE.exists():
        raise RuntimeError(f"Input manifest does not exist: {INPUT_FILE}")

    parsed_cloudinary_url = urlparse(cloudinary_url)
    cloudinary.config(
        cloud_name=parsed_cloudinary_url.hostname,
        api_key=unquote(parsed_cloudinary_url.username or ""),
        api_secret=unquote(parsed_cloudinary_url.password or ""),
        secure=True,
    )
    configuration = cloudinary.config()
    if not all((configuration.cloud_name, configuration.api_key, configuration.api_secret)):
        raise RuntimeError("CLOUDINARY_URL does not contain cloud name, API key, and API secret")
    cloudinary_folder = os.getenv("CLOUDINARY_FOLDER", "stylemind/products").strip("/")
    with INPUT_FILE.open("r", encoding="utf-8-sig", newline="") as file:
        source_rows = list(csv.DictReader(file))
    completed_ids = existing_dataset_ids()

    output_fields = [
        "dataset_id",
        "product_id",
        "variant_id",
        "image_id",
        "catalog_category",
        "name",
        "gender",
        "article_type",
        "color",
        "sku",
        "image_url",
        "cloudinary_public_id",
    ]
    write_header = not OUTPUT_FILE.exists() or OUTPUT_FILE.stat().st_size == 0
    with OUTPUT_FILE.open("a", encoding="utf-8-sig", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=output_fields)
        if write_header:
            writer.writeheader()

        for index, row in enumerate(source_rows, start=1):
            dataset_id = row["id"].strip()
            if dataset_id in completed_ids:
                print(f"[{index}/{len(source_rows)}] Skip {dataset_id}")
                continue

            image_path = IMAGES_ROOT / row["image_file"]
            if not image_path.exists():
                raise RuntimeError(f"Image does not exist: {image_path}")

            product_id = stable_uuid("product", dataset_id)
            variant_id = stable_uuid("variant", dataset_id)
            image_id = stable_uuid("image", dataset_id)
            public_id = f"{cloudinary_folder}/{image_id}"
            print(f"[{index}/{len(source_rows)}] Upload {image_path.name}")
            result = cloudinary.uploader.upload(
                str(image_path),
                public_id=public_id,
                resource_type="image",
                overwrite=False,
            )
            image_url = result.get("secure_url")
            if not image_url or not image_url.startswith("https://"):
                raise RuntimeError(f"Cloudinary did not return an HTTPS URL for {dataset_id}")

            writer.writerow(
                {
                    "dataset_id": dataset_id,
                    "product_id": product_id,
                    "variant_id": variant_id,
                    "image_id": image_id,
                    "catalog_category": row["catalog_category"],
                    "name": row["productDisplayName"],
                    "gender": row["gender"],
                    "article_type": row["articleType"],
                    "color": row["baseColour"],
                    "sku": f"MYNTRA-{dataset_id}",
                    "image_url": image_url,
                    "cloudinary_public_id": public_id,
                }
            )
            output.flush()

    print(f"Created {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
